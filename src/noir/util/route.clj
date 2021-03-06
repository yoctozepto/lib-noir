(ns noir.util.route
  (:use [clout.core :only [route-matches]]
        [noir.request :only [*request*]]
        [noir.response :only [redirect]]))

(defn ^{:skip-wiki true} apply-rules [request rules]
  (if (map? rules)
    (let [{:keys [any every]} rules]
      (and (some #(% request) any) (every? #(% request) every)))
    (every? #(% request) rules)))

(defn ^{:skip-wiki true} check-rules
  [request {:keys [on-fail redirect rules rule]}]
 (let [redirect-target (or redirect "/")
       rules           (or rules [rule])]
   (or (boolean (or (empty? rules) (apply-rules request rules)))
       (if on-fail
         (on-fail request)
         (noir.response/redirect
           (if (fn? redirect-target) (redirect-target request) redirect-target))))))

(defn ^{:skip-wiki true} match-rules
  [req rules]
  (let [route (select-keys req [:uri])]
    (filter (fn [{:keys [uri uris]}]
              (or (and (nil? uri) (nil? uris))
                  (and uri (route-matches uri route))
                  (and uris (some #(route-matches % route) uris))))
            rules)))

(defn ^{:skip-wiki true} wrap-restricted [handler]
     (fn [request]
       (let [rules (:access-rules request)
             matching-rules (match-rules request rules)
             results (map (partial check-rules request) matching-rules)]
         (if (or (empty? results) (every? #{true} results))
          (handler request)
           (first (remove #{true} results))))))

(defmacro restricted
  "Checks if any of the rules defined in wrap-access-rules match the request,
   if no rules match then the response is a redirect to the location specified
   by the noir.util.middleware/wrap-access-rules wrapper, eg:

   (GET \"/foo\" [] (restricted foo-handler))"
  [& body]
     `(wrap-restricted (fn [args#] ~@body)))

(defmacro def-restricted-routes
  "accepts a name and one or more routes, prepends restricted to all
   routes and calls Compojure defroutes, eg:

   (def-restricted-routes private-pages
     (GET \"/profile\" [] (show-profile))
     (GET \"/my-secret-page\" [] (show-secret-page)))

   is equivalent to:

   (defroutes private-pages
     (GET \"/profile\" [] (restricted (show-profile)))
     (GET \"/my-secret-page\" [] (restricted (show-secret-page))))"
  [name & routes]
  `(compojure.core/defroutes ~name
     ~@(for [[method# uri# params# & body#] routes]
         (list method# uri# params# (cons 'noir.util.route/restricted body#)))))
