(ns pallet.crate.svn
  (:use [pallet.action exec-script package])
  (:use [clojure.string :only [join]])
  )

(defn- svn-opt [[k v]]
  (if (instance? Boolean v)
    (when (true? v) (str "--" (name k)))
    (str "--" (name k) " " v)))

(defn- svn-options [options]
  (join " " (map svn-opt (seq options))))
  
(defn- svn-command  
  [cmd {:keys [src dest] :or {dest ""} :as options}]
  (join " "
        ["svn"
         (name cmd)
         (svn-options (dissoc options :src :dest))
         "--no-auth-cache"
         "--non-interactive"
         src
         dest]))
  
(defn svn
  "cmd can either be :install or an svn command (either as string or keyword)
   options are any options that can be passed to an svn command where the key
     will become --key and the value the value attached to the svn option, flags
     that don't take a value (ex: --force) use a boolean (ex: :force true)
   no-auth-cache and non-interactive are included automatically

   The remote repository url is indicated with :src
   The local target directory is indicated with :dest
   If :dest is not passed, the current directory will be used"
  [session cmd & options]
  (case (keyword cmd)
    :install (package session "svn")
    (exec-script session ~(svn-command cmd (apply hash-map options)))))