(ns pallet.crate.lein
  (:use [pallet session])
  (:use [pallet.action directory remote-file exec-script])
  )

(def lein-url
  "https://raw.github.com/technomancy/leiningen/stable/bin/lein")
  
(defn lein
  "Install Leiningen for the admin user"
  [session]
  (let [user (:username (admin-user session))]
    (-> session     
        (directory "bin" :owner user :group user)
        (remote-file "bin/lein" :url lein-url
                     :owner user :group user :mode "u+x")
        (exec-script ~(str "su - " user " -c 'bin/lein self-install'")))))