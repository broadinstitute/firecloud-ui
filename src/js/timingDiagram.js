// draws cromwell timing diagram for a submission  Cromwell 19

function addDataTableRow(dataTable, callName, callPartName, startDate, endDate) {
    if (startDate <= endDate) {
        dataTable.addRow([callName, callPartName, startDate, endDate]);
    } // else {
      //  console.error("Unable to add '" + callName + "'s entry: '" + callPartName + "' because start-time '" + startDate + "'' is greater than end-time '" + endDate + "'");
    //}
}

function timingDiagram(element, response, workflowName, height) {
    var container = element;
    var chart = new google.visualization.Timeline(container);
    var data = JSON.parse(response);

    var dataTable = new google.visualization.DataTable();
    dataTable.addColumn({ type: 'string', id: 'Position' });
    dataTable.addColumn({ type: 'string', id: 'Name' });
    dataTable.addColumn({ type: 'date', id: 'Start' });
    dataTable.addColumn({ type: 'date', id: 'End' });

    var executionCallsCount = 0;
    //var workflowName = data.workflowName;
    var workflowEnd = null;
    if (data.hasOwnProperty("end")) {
        workflowEnd = new Date(data.end);
    }

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
            var attempt = (typeof callList[callIndex]["attempt"] != "undefined") ? callList[callIndex]["attempt"] : null;
            var callStatus = callList[callIndex].executionStatus;

            // add the index of the shard if there is one
            var thisCallName = (index == -1 ? callName : callName + "." + index);

            // add the retry number, unless this was a successfuly first attempt (for brevity)
            thisCallName = (callStatus == "Done" && attempt === 1 ? thisCallName :
            	( attempt == null) ? thisCallName : thisCallName + ".retry-" + attempt)

            // Remove the workflow name
            //thisCallName = thisCallName.replace(new RegExp("^" + workflowName + "\\."), "");

            var executionEvents = callList[callIndex].executionEvents;

            var firstEventStart = null;
            var finalEventEnd = null;

            if(callStatus == "Done" || callStatus == "Failed" || callStatus == "Preempted" || callStatus == "RetryableFailure") {
                executionCallsCount++;
                //for (var executionEventIndex in executionEvents) {
                for (executionEventIndex=0, len=executionEvents.length; executionEventIndex < len; executionEventIndex++) {
                    var executionEvent = callList[callIndex].executionEvents[executionEventIndex];
                    var description = executionEvent["description"];
                    // Replace all "start(...)" with just "start" so that the names (and therefore the "color" assigned from the list below) are consistent:
                    //description = description.replace(new RegExp("^start.*"), "start");
                    var startDate = new Date(executionEvent["startTime"]);
                    var endDate = new Date(executionEvent["endTime"]);
                    if (firstEventStart == null || startDate < firstEventStart) {
                        firstEventStart = startDate
                    }
                    if (finalEventEnd == null || endDate > finalEventEnd) {
                        finalEventEnd = endDate
                    }
                    // NB: See the column definitions above.
	                addDataTableRow(dataTable, thisCallName, description, startDate, endDate);
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
                    addDataTableRow(dataTable, thisCallName, callList[callIndex].executionStatus, callStart, callEnd);
                } else {
                    if (callStart < firstEventStart) addDataTableRow(dataTable, thisCallName, "cromwell starting overhead", callStart, firstEventStart);
                    if (callEnd > finalEventEnd) addDataTableRow(dataTable, thisCallName, "cromwell final overhead", finalEventEnd, callEnd);
                }
            } else if (callList[callIndex].executionStatus == "Running") {
                executionCallsCount++;
                var endDate = workflowEnd;
                if(endDate == null) {
                    addDataTableRow(dataTable, thisCallName, "Running", new Date(callList[callIndex].start), new Date(Date.now()));
                }
                else {
                    addDataTableRow(dataTable, thisCallName, "Still running when workflow ended", new Date(callList[callIndex].start), endDate);
                }
            } else if (callList[callIndex].executionStatus == "Starting") {
                executionCallsCount++;
                var endDate = new Date(Date.now());
                addDataTableRow(dataTable, thisCallName, "Starting", new Date(callList[callIndex].start), endDate);
            }
        }
    }

    var options = {
        colors: ['#04E9E7', '#009DF4', '#0201F4', '#01FC01', '#00C400', '#008C00', '#CCAD51', '#2dd801', '#F99200', '#9854C6', '#F800FD', '#BC0000', '#FD0000'],        
        backgroundColor: '#ffffff',
        height: height,
        timeline: {
            avoidOverlappingGridLines: false,
            showBarLabels: false,
            rowLabelStyle: { fontName: 'Roboto', fontSize: 12, color: '#333' },   //font-size 10   #603913
            // Although bar labels are unshown, they still affect the height of each row. So make it small.
            barLabelStyle: { fontName: 'Roboto', fontSize: 8, color: '#333' },    //#603913
        }
    };
    chart.draw(dataTable, options);
};

// stored to preserve function calls through Closure
window['addDataTableRow'] = addDataTableRow;
window['timingDiagram'] = timingDiagram;
