(ns leiningen.appengine-prepare
  "Prepares a the Google App Engine application for deployment."
  (:use appengine-magic.utils)
  (:require leiningen.compile leiningen.jar leiningen.util.file lancet)
  (:import java.io.File))


(defn appengine-prepare [project]
  (let [prj-application (or (:appengine-application project) (:name project))
        prj-display-name (or (:appengine-display-name project) (:name project))
        prj-servlet (or (:appengine-entry-servlet project) "app_servlet")
        resources-dir (File. (:resources-path project))
        lib-dir (File. (:library-path project))
        lib-dev-dir (File. lib-dir "dev")
        WEB-INF-dir (File. resources-dir "WEB-INF")
        target-lib-dir (File. WEB-INF-dir "lib")
        target-app-jar (File. target-lib-dir (str prj-application ".jar"))
        compile-path (File. (:compile-path project))
        compile-path-exists? (.isDirectory compile-path)
        compile-path-empty? (= 0 (-> compile-path .list seq count))]
    (println "preparing App Engine application" prj-display-name "for deployment")
    ;; compile all
    (let [project-with-aot (if (contains? project :aot)
                               project
                               ;; For projects which do not define any
                               ;; namespaces for AOT compilation, just include
                               ;; the entry-point servlet.
                               (assoc project
                                 :aot [(symbol (format "%s.%s"
                                                       (_dash prj-application) prj-servlet))]))]
      (leiningen.compile/compile project-with-aot))
    ;; delete existing content of target lib/
    (lancet/delete {:dir (.getPath target-lib-dir)})
    ;; prepare destination lib/ directory
    (lancet/mkdir {:dir target-lib-dir})
    ;; make a jar of the compiled app, and put it in WEB-INF/lib
    (leiningen.jar/jar project)
    (lancet/move {:file (leiningen.jar/get-jar-filename project)
                  :todir (.getPath target-lib-dir)})
    ;; projects which do not normally use AOT may need some cleanup
    (when-not (contains? project :aot)
      (cond
       ;; never had a classes/ directory; unlikely with Leiningen
       (not compile-path-exists?)
       (leiningen.util.file/delete-file-recursively compile-path true)
       ;; had an empty classes/ directory
       compile-path-empty?
       (doseq [entry-name (.list compile-path)]
         (let [entry (File. compile-path entry-name)]
           (leiningen.util.file/delete-file-recursively entry true)))))
    ;; copy important dependencies into WEB-INF/lib
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset {:dir lib-dir :includes "*" :excludes "dev"}))
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset
                  {:dir lib-dev-dir
                   :includes "appengine-magic*,ring-core*,appengine-api-1.0-sdk*,appengine-api-labs*"}))))
