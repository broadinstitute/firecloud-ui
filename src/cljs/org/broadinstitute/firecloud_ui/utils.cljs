(ns org.broadinstitute.firecloud-ui.utils)


(defn call-external-object-method [obj method-name & args]
  "Call an external object's method by name, since a normal call will get renamed during
   advanced compilation and cause an error."
  (apply (.bind (aget obj (name method-name)) obj) args))
