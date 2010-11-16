(ns leiningen.appengine-prepare
  "Prepares a the Google App Engine application for deployment."
  (:use appengine-magic.utils)
  (:require leiningen.compile leiningen.jar lancet)
  (:import java.io.File))


(defn appengine-prepare [project]
  (let [prj-application (or (:appengine-application project) (:name project))
        prj-display-name (or (:appengine-display-name project) (:name project))
        resources-dir (File. (:resources-path project))
        lib-dir (File. (:library-path project))
        lib-dev-dir (File. lib-dir "dev")
        WEB-INF-dir (File. resources-dir "WEB-INF")
        target-lib-dir (File. WEB-INF-dir "lib")
        target-app-jar (File. target-lib-dir (str prj-application ".jar"))]
    (println "preparing App Engine application" prj-display-name "for deployment")
    ;; compile all
    (leiningen.compile/compile project)
    ;; delete existing content of target lib/
    (lancet/delete {:dir (.getPath target-lib-dir)})
    ;; prepare destination lib/ directory
    (lancet/mkdir {:dir target-lib-dir})
    ;; make a jar of the compiled app, and put it in WEB-INF/lib
    (leiningen.jar/jar project)
    (lancet/move {:file (leiningen.jar/get-jar-filename project)
                  :todir (.getPath target-lib-dir)})
    ;; copy important dependencies into WEB-INF/lib
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset {:dir lib-dir :includes "*" :excludes "dev"}))
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset
                  {:dir lib-dev-dir
                   :includes "appengine-magic*,ring-core*,appengine-api-1.0-sdk*,appengine-api-labs*"}))))
