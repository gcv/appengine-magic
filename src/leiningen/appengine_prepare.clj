(ns leiningen.appengine-prepare
  "Prepares a the Google App Engine application for deployment."
  (:use appengine-magic.utils)
  (:require leiningen.compile leiningen.jar leiningen.util.file
            [lancet.core :as lancet])
  (:import java.io.File))


(defn appengine-prepare [project]
  (let [prj-application (or (:appengine-application project) (:name project))
        prj-display-name (or (:appengine-display-name project) (:name project))
        prj-servlet (or (:appengine-entry-servlet project) "app_servlet")
        war-dir (File. (or (:appengine-app-war-root project) "war"))
        lib-dir (File. (:library-path project))
        lib-dev-dir (File. lib-dir "dev")
        web-inf-dir (File. war-dir "WEB-INF")
        target-lib-dir (File. web-inf-dir "lib")
        compile-path (File. (:compile-path project))
        compile-path-exists? (.isDirectory compile-path)
        compile-path-empty? (= 0 (-> compile-path .list seq count))]
    (println "preparing App Engine application" prj-display-name "for deployment")
    ;; compile all; when successful (status is 0), continue to prepare
    (when (= 0 (leiningen.compile/compile (if (contains? project :aot)
                                              project
                                              (assoc project
                                                :keep-non-project-classes true
                                                :aot [(symbol (format "%s.%s"
                                                                      (_dash prj-application)
                                                                      prj-servlet))]))))
      ;; delete existing content of target lib/
      (lancet/delete {:dir (.getPath target-lib-dir)})
      ;; prepare destination lib/ directory
      (lancet/mkdir {:dir target-lib-dir})
      ;; make a jar of the compiled app, and put it in WEB-INF/lib
      (leiningen.jar/jar (merge project
                                {:omit-source true
                                 :jar-exclusions [#"^WEB-INF/appengine-generated.*$"]}))
      (lancet/move {:file (leiningen.jar/get-jar-filename project)
                    :todir (.getPath target-lib-dir)})
      ;; copy important dependencies into WEB-INF/lib
      (lancet/copy {:todir (.getPath target-lib-dir)}
                   (lancet/fileset {:dir lib-dir :includes "*" :excludes "dev"}))
      (lancet/copy {:todir (.getPath target-lib-dir)}
                   (lancet/fileset
                    {:dir lib-dev-dir
                     :includes (str "appengine-magic*,ring-core*,"
                                    "commons-io*,commons-codec*,commons-fileupload*,"
                                    "appengine-api-1.0-sdk*,appengine-api-labs*")})))
    ;; Projects which do not normally use AOT may need some cleanup. This should
    ;; happen regardless of compilation success or failure.
    (when-not (contains? project :aot)
      (cond
       ;; never had a classes/ directory; unlikely with Leiningen
       (not compile-path-exists?)
       (leiningen.util.file/delete-file-recursively compile-path true)
       ;; had an empty classes/ directory
       compile-path-empty?
       (doseq [entry-name (.list compile-path)]
         (let [entry (File. compile-path entry-name)]
           (leiningen.util.file/delete-file-recursively entry true)))))))
