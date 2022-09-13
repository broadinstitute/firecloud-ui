(ns broadfcuitest.common.gcs-file-preview
  (:require
   [cljs.test :refer [deftest is testing]]
   [broadfcui.common.gcs-file-preview :as preview]
   [broadfcui.utils :as utils]
   ))


(defn test-previewable
  ([filename]
    (testing (str filename " files are previewable")
      (is (preview/previewable? (str "subdirectory/my." filename)))))
  ([filename content-type]
    (testing (str filename " files of type " content-type " are previewable")
        (is (preview/previewable? (str "subdirectory/my." filename) content-type)))))

(defn test-not-previewable
  ([filename]
    (testing (str filename " files are not previewable")
      (is (not (preview/previewable? (str "subdirectory/my." filename))))))
 ([filename content-type]
    (testing (str filename " files of type " content-type " are not previewable")
      (is (not (preview/previewable? (str "subdirectory/my." filename) content-type))))))

(def yes-filetypes ["fastq" "vcf" "txt" "json" "log"])
(def no-filetypes ["bam" "bai" "pdf" "jpg" "jpeg" "png" "gif" "bmp"])

(def yes-contenttypes ["text/plain" "application/json"])
(def no-contenttypes ["application/octet-stream" "image/png"])


(deftest previewable-by-filename
  (doseq [o no-filetypes]
    (test-not-previewable o))
  (doseq [o yes-filetypes]
    (test-previewable o))
  (testing "a filename containing a mid-string previewable extension but ending otherwise is not previewable"
    (is (not (preview/previewable? "subdirectory.log/log.log.log.bam"))))
  (testing "a filename containing a mid-string non-previewable extension but ending otherwise is previewable"
    (is (preview/previewable? "subdirectory.bam/pdf.png.gif.txt")))
)

(deftest previewable-by-content-type
  ;; if the content type is defined as previewable, it's previewable regardless of file extension.
  (doseq [c yes-contenttypes]
    (doseq [o (concat yes-filetypes no-filetypes)]
      (test-previewable o c)
    )
  )
  ;; if the content type is not previewable, we fall back to inspecting the file extension.
  (doseq [c no-contenttypes]
    (doseq [o yes-filetypes]
      (test-previewable o c)
    )
  )
  (doseq [c no-contenttypes]
    (doseq [o no-filetypes]
      (test-not-previewable o c)
    )
  )
)

; (cljs.test/run-tests)
