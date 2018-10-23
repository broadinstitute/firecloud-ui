// draws cromwell timing diagram for a submission. Last synced with Cromwell 34.

// ======================= START code used as-is from Cromwell =======================
/*
    following code, up until the END marker, is copied directly from Cromwell WITH THREE
    EXCEPTIONS. Each of the three exceptions is preceded by a comment. These comments
    all start with "DA:" and end with "for FireCloud."
*/
var parentWorkflowNames = [];
var expandedParentWorkflows = [];
var chartView;

function addDataTableRow(dataTable, callName, callPartName, startDate, endDate, ancestry) {
    if (startDate <= endDate) {
        newRow = dataTable.addRow([callName, callPartName, startDate, endDate]);
        dataTable.setRowProperty(newRow, "ancestry", ancestry);
    } else {
        console.warn("Unable to add '" + callName + "'s entry: '" + callPartName + "' because start-time '" + startDate + "'' is greater than end-time '" + endDate + "'");
    }
}

function parseMetadata(data, dataTable, ancestry) {
    var workflowName = data.workflowName;
    var workflowEnd = null;
    if (data.hasOwnProperty("end")) {
        workflowEnd = new Date(data.end);
    }
    var executionCallsCount = 0;
    var callsList = data.calls;
    var sortable = [];
    for (var key in callsList) {
        sortable.push([key, callsList[key]]);
    }
    sortable.sort(function(a,b) {
        return new Date(a[1][0].start) - new Date(b[1][0].start);
    });

    for(var sortableIndex in sortable)
    {
        var callName = sortable[sortableIndex][0];
        var callList = sortable[sortableIndex][1];

        for (var callIndex in callList)
        {
            var index = callList[callIndex]["shardIndex"];
            var attempt = callList[callIndex]["attempt"];
            var callStatus = callList[callIndex].executionStatus;

            // add the index of the shard if there is one
            var callLqn = (index == -1 ? callName : callName + "." + index);

            // add the retry number, unless this was a successfuly first attempt (for brevity)
            callLqn = ( callStatus == "Done" && attempt == 1 ? callLqn : callLqn + ".retry-" + attempt);

            // Remove the workflow name
            // DA: this line commented out for FireCloud.
            // callLqn = callLqn.replace(new RegExp("^" + workflowName + "\\."), "");

            var callFqn;
            if (ancestry.length == 0) {
                callFqn = callLqn;
            } else {
                callFqn = ancestry.join("/") + "/" + callLqn;
            }

            var executionEvents = callList[callIndex].executionEvents;

            var firstEventStart = null;
            var finalEventEnd = null;

            // DA: added Aborted and Bypassed statuses to this line for FireCloud.
            if(callStatus == "Done" || callStatus == "Failed" || callStatus == "RetryableFailure" || callStatus == "Aborted" || callStatus == "Bypassed") {
                executionCallsCount++;
                for (var executionEventIndex in executionEvents) {
                    var executionEvent = callList[callIndex].executionEvents[executionEventIndex];
                    var description = executionEvent["description"];
                    // Replace all "start(...)" with just "start" so that the names (and therefore the "color" assigned from the list below) are consistent:
                    description = description.replace(new RegExp("^start.*"), "start");
                    var startDate = new Date(executionEvent["startTime"]);
                    var endDate = new Date(executionEvent["endTime"]);
                    if (firstEventStart == null || startDate < firstEventStart) {
                        firstEventStart = startDate
                    }
                    if (finalEventEnd == null || endDate > finalEventEnd) {
                        finalEventEnd = endDate
                    }
                    // NB: See the column definitions above.
                    addDataTableRow(dataTable, callFqn, description, startDate, endDate, ancestry);
                }

                if (callList[callIndex].hasOwnProperty("start")) {
                    var callStart = new Date(callList[callIndex].start);
                }
                else {
                    var callStart = firstEventStart
                }
                if (callList[callIndex].hasOwnProperty("end")) {
                    var callEnd = new Date(callList[callIndex].end);
                }
                else {
                    var callStart = finalEventEnd
                }

                if (firstEventStart == null || finalEventEnd == null) {
                    addDataTableRow(dataTable, callFqn, callList[callIndex].executionStatus, callStart, callEnd, ancestry);
                } else {
                    if (callStart < firstEventStart) addDataTableRow(dataTable, callFqn, "cromwell starting overhead", callStart, firstEventStart, ancestry);
                    if (callEnd > finalEventEnd) addDataTableRow(dataTable, callFqn, "cromwell final overhead", finalEventEnd, callEnd, ancestry);
                }
            } else if (callList[callIndex].executionStatus == "Running" || callList[callIndex].executionStatus == "QueuedInCromwell" || callList[callIndex].executionStatus == "Starting") {
                var status = callList[callIndex].executionStatus
                executionCallsCount++;
                // DA: added the "or now()" safeguard for anything still in Starting or otherwise doesn't have a workflowEnd value, for FireCloud.
                var endDate = workflowEnd || new Date(Date.now());
                if(endDate == null) {
                    addDataTableRow(dataTable, callFqn, status, new Date(callList[callIndex].start), new Date(Date.now()), ancestry);
                }
                else {
                    addDataTableRow(dataTable, callFqn, "Still ".concat(status).concat(" when workflow ended"), new Date(callList[callIndex].start), endDate, ancestry);
                }
            }

            if (callList[callIndex].hasOwnProperty("subWorkflowMetadata")) {
                var clone = ancestry.slice(0);
                clone.push(callLqn);
                parentWorkflowNames.push(callFqn);
                executionCallsCount += parseMetadata(callList[callIndex].subWorkflowMetadata, dataTable, clone);
            }
        }
    }

    return executionCallsCount;
}

function toggleSubWorkflowRows(dt, selectedRow) {
    function filterFunction(cell, row, column, table) {
        var ancestries = table.getRowProperty(row, "ancestry");
        var ancestryFqn = ancestries.join("/");
        // Display the row if it's not a sub workflow or if its parent is in the list of workflows to expand.
        return (ancestries.length == 0 || (expandedParentWorkflows.indexOf(ancestryFqn) != -1));
    }

    var filter = {
        test: filterFunction,
        // Not used because all filtering is done in filterFunction but it's mandatory to have something else than the filter
        column: 0
    };

    var parentWorkflow;
    parentWorkflow = chartView.getValue(selectedRow, 0);

    var indexOfParentWorkflow = expandedParentWorkflows.indexOf(parentWorkflow);

    if (indexOfParentWorkflow != -1) {
        // Remove the parent workflow and its children from the list
        expandedParentWorkflows = expandedParentWorkflows.filter(function (el, i, array) {
            return !el.startsWith(parentWorkflow)
        })
    } else if (parentWorkflow && parentWorkflowNames.indexOf(parentWorkflow) != -1) {
        // Add it if it's not
        expandedParentWorkflows.push(parentWorkflow);
    }

    var rowsToDisplay = dt.getFilteredRows([filter]);
    var view = new google.visualization.DataView(dt);
    view.setRows(rowsToDisplay);
    return view;
}

function hideAllSubWorkflows(dt) {
    var view = new google.visualization.DataView(dt);
    function filterFunction(cell, row, column, table) {
        return table.getRowProperty(row, "ancestry").length != 0;
    }

    view.hideRows(dt.getFilteredRows([{column: 0, test: filterFunction}]));
    return view;
}
// ======================= END code used as-is from Cromwell =======================

// ======================= START code modified from Cromwell for FireCloud environment =======================
/*
    following code, up until the END marker, is loosely based on Cromwell's JavaScript, but is significantly
    modified. Cromwell's drawChart() function embeds an ajax call that FireCloud wants to avoid, since we already
    have the data. Furthermore, FireCloud changes the colors and fonts to fit in better with the FireCloud UI.

    FireCloud's timingDiagram() function replaces Cromwell's drawChart().
*/

function timingDiagram(element, response, workflowName, height) {
    var data = JSON.parse(response);

    var dataTable = new google.visualization.DataTable();
    dataTable.addColumn({ type: 'string', id: 'Position' });
    dataTable.addColumn({ type: 'string', id: 'Name' });
    dataTable.addColumn({ type: 'date', id: 'Start' });
    dataTable.addColumn({ type: 'date', id: 'End' });

    // parseMetadata calls addDataTableRow as a side effect
    parseMetadata(data, dataTable, []);

    // Cromwell's visual options:
    // var options = {
    //     colors: ['#33178f', '#c5bc12', '#5e8229', '#9a03f8', '#9e4653', '#4ef2ca', '#2dd801', '#3f7b49', '#763097', '#bc8b28', '#cb716f', '#adce53', '#c3768d', '#fdf92a'],
    //     height: executionCallsCount * 18 + 60,
    //     timeline: {
    //         avoidOverlappingGridLines: false,
    //         showBarLabels: false,
    //         rowLabelStyle: { fontName: 'Helvetica', fontSize: 10, color: '#603913' },
    //         // Although bar labels are unshown, they still affect the height of each row. So make it small.
    //         barLabelStyle: { fontName: 'Helvetica', fontSize: 5, color: '#603913' },
    //     },
    //     tooltip: {isHtml: true}
    // };

    // FireCloud's visual options:
    var options = {
        colors: ['#04E9E7', '#009DF4', '#0201F4', '#01FC01', '#00C400', '#008C00', '#CCAD51', '#2dd801', '#F99200', '#9854C6', '#F800FD', '#BC0000', '#FD0000', '#fdf92a'],
        backgroundColor: '#ffffff',
        height: height,
        timeline: {
            avoidOverlappingGridLines: false,
            showBarLabels: false,
            rowLabelStyle: { fontName: 'Roboto', fontSize: 12, color: '#333' },
            // Although bar labels are unshown, they still affect the height of each row. So make it small.
            barLabelStyle: { fontName: 'Roboto', fontSize: 8, color: '#333' },
        },
        tooltip: {isHtml: true}
    };

    var container = element;
    var chart = new google.visualization.Timeline(container);
    chartView = hideAllSubWorkflows(dataTable);

    chart.draw(chartView, options);

    google.visualization.events.addListener(chart, 'select', selectHandler);

    function selectHandler(e) {
        var selectedItem = chart.getSelection()[0];
        if (selectedItem) {
            chartView = toggleSubWorkflowRows(dataTable, selectedItem.row);
            chart.draw(chartView, options);
            // For some reason the tooltip gets stuck and doesn't disappear by itself, so remove it explicitly from the DOM
            $( ".google-visualization-tooltip" ).remove();
        }
    }
}
// ======================= END code modified from Cromwell for FireCloud environment =======================

// stored to preserve function calls through Closure
window['addDataTableRow'] = addDataTableRow;
window['parseMetadata'] = parseMetadata;
window['toggleSubWorkflowRows'] = toggleSubWorkflowRows;
window['hideAllSubWorkflows'] = hideAllSubWorkflows;
window['timingDiagram'] = timingDiagram;
