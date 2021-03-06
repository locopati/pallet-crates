(ns pallet.crate.haproxy-test
  (:use pallet.crate.haproxy)
  (:require
   [pallet.compute :as compute]
   [pallet.session :as session]
   [pallet.build-actions :as build-actions]
   [pallet.action.remote-file :as remote-file]
   [pallet.crate.etc-default :as etc-default]
   [pallet.test-utils :as test-utils])
  (:use clojure.test))

(deftest format-k-v-test
  (is (= "a b "
         (#'pallet.crate.haproxy/format-kv :a "b" " ")))
  (is (= "a b\n"
         (#'pallet.crate.haproxy/format-kv :a "b" \newline)))
  (is (= "a b\na c\n"
         (#'pallet.crate.haproxy/format-kv :a ["b" "c"] \newline))))

(deftest config-server-test
  (is (= "tag 1.2.3.4:80 fall 3 check "
         (#'pallet.crate.haproxy/config-server
          {:name :tag :ip "1.2.3.4" :server-port 80 :fall 3 :check true}))))

(deftest proxied-by-test
  (let [node (test-utils/make-node "tag" :public-ips ["1.2.3.4"])]
    (is (= {:parameters
            {:haproxy
             {:tag1
              {:app1 [{:name (format "tag%s" (session/safe-id
                                              (compute/id node)))
                       :ip "1.2.3.4"}]}}},
            :server {:group-name :tag :node node :node-id :tag-1-2-3-4}}
           (proxied-by
            (test-utils/target-server
             :group-name :tag :node node :node-id (keyword (compute/id node)))
            :tag1 :app1)))))


(deftest merge-servers-test
  (let [node (test-utils/make-node "tag" :public-ips ["1.2.3.4"])]
    (is (= {:listen {:app1 {:server ["tag 1.2.3.4 check "]
                            :balance "round-robin"
                            :server-address "0.0.0.0:80"}}}
           (#'pallet.crate.haproxy/merge-servers
            {:parameters
             {:haproxy
              {:tag1
               {:app1 [{:name "tag", :ip "1.2.3.4" :check true}]}}}
             :server {:group-name :tag1 :node node}}
            {:listen
             {:app1 {:server-address "0.0.0.0:80"
                     :balance "round-robin"}}})))
    (is (= {:listen {:app1 {:balance "round-robin"}}}
           (#'pallet.crate.haproxy/merge-servers
            {}
            {:listen
             {:app1 {:balance "round-robin"}}})))))

(deftest config-section-test
  (is (= (str
          "listen app1 0.0.0.0:80\n"
          "server tag 1.2.3.4 name tag \n"
          "balance round-robin\n")
         (#'pallet.crate.haproxy/config-section
          [:listen {:app1 {:server ["tag 1.2.3.4 name tag "]
                           :server-address "0.0.0.0:80"
                           :balance "round-robin"}}])))
  (is (= (str
          "listen app1 0.0.0.0:80\n"
          "server tag 1.2.3.4 name tag \n"
          "balance round-robin\n")
         (apply str
                (map
                 #'pallet.crate.haproxy/config-section
                 {:listen {:app1 {:server ["tag 1.2.3.4 name tag "]
                                  :server-address "0.0.0.0:80"
                                  :balance "round-robin"}}})))))

(deftest configure-test
  (is (=
       (first
        (build-actions/build-actions
         {:server {:image {:os-family :ubuntu}
                   :group-name :tag
                   :node (test-utils/make-node
                          "tag" :public-ips ["1.2.3.4"])}}
         (remote-file/remote-file
          "/etc/haproxy/haproxy.cfg"
          :content "global\nlog 127.0.0.1 local0\nlog 127.0.0.1 local1 notice\nmaxconn 4096\nuser haproxy\ngroup haproxy\ndaemon\ndefaults\nmode http\nlisten app 0.0.0.0:80\nserver h1 1.2.3.4:80 weight 1 maxconn 50 check\nserver h2 1.2.3.5:80 weight 1 maxconn 50 check\n"
          :literal true)
         (etc-default/write "haproxy" :ENABLED 1)))
       (first
        (build-actions/build-actions
         {:server {:image {:os-family :ubuntu}
                   :group-name :tag
                   :node (test-utils/make-node
                          "tag" :public-ips ["1.2.3.4"])}}
         (configure
          :listen {:app
                   {:server-address "0.0.0.0:80"
                    :server ["h1 1.2.3.4:80 weight 1 maxconn 50 check"
                             "h2 1.2.3.5:80 weight 1 maxconn 50 check"]}}
          :defaults {:mode "http"}))))))

(deftest invocation-test
  (let [node (test-utils/make-node "tag" :ip "1.2.3.4")]
    (is (build-actions/build-actions
         {:server {:image {:os-family :ubuntu}
                   :group-name :tag :node node
                   :node-id (keyword (compute/id node))}}
         (install-package)
         (configure
          :listen {:app
                   {:server-address "0.0.0.0:80"
                    :server ["h1 1.2.3.4:80 weight 1 maxconn 50 check"
                             "h2 1.2.3.5:80 weight 1 maxconn 50 check"]}})
         (proxied-by :tag :app)))))
