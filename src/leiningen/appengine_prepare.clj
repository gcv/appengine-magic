(ns leiningen.appengine-prepare
  "Prepares a the Google App Engine application for deployment."
  (:use appengine-magic.utils
        [leiningen.core.main :only [abort]])
  (:require leiningen.compile leiningen.jar leiningen.clean
            [leiningen.core.project :as lein-project]
            [leiningen.core.classpath :as classpath]
            [me.raynes.fs :as fs]
            [clojure.string :as string])
  (:import java.io.File))

(defn- copy-to-dir [file dir]
  (let [dir (if (isa? (type dir) File) dir (File. dir))
        file-path (if (isa? (type file) File) (.getPath file) file)
        dest-file (File. dir (fs/base-name file))]
    (println "Copying" file-path "to" (.getPath dir))
    (fs/copy file dest-file)))

(defn appengine-prepare [project]
  (let [project (lein-project/set-profiles project (dissoc (:profiles project) :dev) [:dev])
        prj-application (or (:appengine-application project) (:name project))
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
        ;; Leiningen will throw an exception if compile has failed
        (leiningen.compile/compile project)
        ;; delete existing content of target lib/
        (fs/delete-dir target-lib-dir)
        ;; prepare destination lib/ directory
        (fs/mkdir target-lib-dir)
        ;; make a jar of the compiled app, and put it in WEB-INF/lib
        (let [{jar-file [:extension "jar"]} (leiningen.jar/jar (merge project
                                  {:omit-source true
                                   :jar-exclusions [#"^WEB-INF/appengine-generated.*$"]}))]
          (copy-to-dir jar-file target-lib-dir))
        ;; copy important dependencies into WEB-INF/lib
        (doseq [dep dependencies]
          (copy-to-dir dep target-lib-dir))
      )

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
