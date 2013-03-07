(ns leiningen.appengine-prepare
  "Prepares a the Google App Engine application for deployment."
  (:use appengine-magic.utils
        [leiningen.core.main :only [abort]])
  (:require leiningen.compile leiningen.jar leiningen.clean
            [leiningen.core.classpath :as classpath]
            ;; FIXME: Remove the Lancet dependency.
            [lancet.core :as lancet])
  (:import java.io.File))


(defn appengine-prepare [project]
  (let [prj-application (or (:appengine-application project) (:name project))
        prj-display-name (or (:appengine-display-name project) (:name project))
        prj-servlet (or (:appengine-entry-servlet project) "app_servlet")
        dependencies (classpath/resolve-dependencies :dependencies project) ; FIXME: Does this work?
        war-dir (File. (or (:appengine-app-war-root project) "war"))
        web-inf-dir (File. war-dir "WEB-INF")
        target-lib-dir (File. web-inf-dir "lib")
        compile-path (File. (:compile-path project))
        compile-path-exists? (.isDirectory compile-path)
        compile-path-empty? (= 0 (-> compile-path .list seq count))]
    (println dependencies)
    (println "preparing App Engine application" prj-display-name "for deployment")
    ;; check for basic correctness
    (when (some (fn [x] (= 'appengine-magic (first x)))
                (:dependencies project))
      (abort "project.clj error: put appengine-magic in :dev-dependencies, not :dependencies"))
    ;; compile all; when successful (status is 0), continue to prepare
    (let [project (if (contains? project :aot)
                      project
                      (assoc project
                        :keep-non-project-classes true
                        :aot [(symbol (format "%s.%s"
                                              (_dash prj-application)
                                              prj-servlet))]))]
      (when (= 0 (leiningen.compile/compile project))
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
        ;; FIXME: This needs to exclude development-only dependencies.
        (lancet/copy {:todir (.getPath target-lib-dir)}
                     (lancet/fileset {:dir target-lib-dir
                                      :includes "*"}))))
    ;; Projects which do not normally use AOT may need some cleanup. This should
    ;; happen regardless of compilation success or failure.
    (when-not (contains? project :aot)
      (cond
       ;; never had a classes/ directory; unlikely with Leiningen
       (not compile-path-exists?)
       (leiningen.clean/delete-file-recursively compile-path true)
       ;; had an empty classes/ directory
       compile-path-empty?
       (doseq [entry-name (.list compile-path)]
         (let [entry (File. compile-path entry-name)]
           (leiningen.clean/delete-file-recursively entry true)))))))
