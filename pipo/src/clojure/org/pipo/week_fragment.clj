(ns org.pipo.week-fragment
  (:require [neko.activity :refer [simple-fragment]]
            [neko.debug :refer [*a]]
            [neko.ui :refer [config]]
            [neko.find-view :refer [find-view]]
            [org.pipo.log :as log]
            )
  (:import android.support.v4.view.ViewPager)
  (:gen-class
    :prefix "fragment-"
    :extends android.support.v13.app.FragmentStatePagerAdapter
    :implements [android.support.v4.view.ViewPager$OnPageChangeListener]
    :state state
    :init init
    :constructors {[android.content.Context
                    android.app.FragmentManager
                    android.support.v4.view.ViewPager]
                   [android.app.FragmentManager]}
    ))

(defn- int-to-year-week [i]
  )

;; little functions to safely set the fields.
(defn- setfield
  [^org.pipo.week_fragment this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [^org.pipo.week_fragment this key]
  (@(.state this) key))

(defn fragment-init [ctx fm view-pager]
  ; mViewPager.setOnPageChangeListener(this);

  [[fm] (atom {:middle (/ Integer/MAX_VALUE 2)
               :ctx ctx
               :vp view-pager
               :focused-page 1
               :global-pos 1
               })])

    ; public DemoCollectionPagerAdapter(FragmentManager fm) {
    ;     super(fm);
    ; }

    ; @Override
    ; public Fragment getItem(int i) {
(defn fragment-getItem [this i]
    ;     Fragment fragment = new DemoObjectFragment();
    ;     Bundle args = new Bundle();
    ;     // Our object is just an integer :-P
    ;     args.putInt(DemoObjectFragment.ARG_OBJECT, i + 1);
    ;     fragment.setArguments(args);
    ;     return fragment;

    (log/d "getItem called")
    (simple-fragment
      (getfield this :ctx)
      [:linear-layout {:orientation :vertical
                       :id-holder true
                       :id i}
       [:text-view {:id ::fragment-text
                    :text (str "fragment " i ", global: " (getfield this :global-pos))}]])
    )
    ; }

    ; @Override
    ; public int getCount() {
(defn fragment-getCount [this]
  ; java.lang.Integer
  ; Integer/MAX_VALUE
  3
    )

    ; @Override
    ; public CharSequence getPageTitle(int position) {
    ;     return "OBJECT " + (position + 1);
    ; }

; (defn fragment-instantiateItem [this container position]


(defn- set-page-content [view global-pos]
  (if (nil? view)
    (log/d "set-page-content view == nil")
    (do
      (log/d  "set-page-content view: " view)
      (config (find-view view ::fragment-text)
              :text (str "fragment " (.getId view) ", global: " global-pos))
    ;     TextView tv = (TextView) viewLayout.findViewById(R.id.calendar_text);
    ;     tv.setText(String.format("Text Text Text global %d", globalPosition));
    )
    )
  )


(defn fragment-onPageScrollStateChanged [this state]
  (log/d "fragment-onPageScrollStateChanged")
  (if (= state ViewPager/SCROLL_STATE_IDLE)
    (do

      (let [global-pos (getfield this :global-pos)
            focused-page (getfield this :focused-page)]
        (log/d "focused: " focused-page ", global: " global-pos)
        (if (= focused-page 0)
          (setfield this :global-pos (dec global-pos)))
        (if (= focused-page 2)
          (setfield this :global-pos (inc global-pos)))
        (log/d "new global: " (getfield this :global-pos))



      ; (log/d "viewPager child cound: " (.getChildCount (getfield this :vp)))
      ; (log/d "viewPager child: " (.getChildAt (getfield this :vp) focused-page))
      ; (log/d "viewPager child id: " (.getId (.getChildAt (getfield this :vp) focused-page)))



      ; for (int i = 0; i < viewPager.getChildCount(); i++)
      (doseq [i (range (.getChildCount (getfield this :vp)))]
            (let [view (.getChildAt (getfield this :vp) i)
                  id (.getId view)]

              (if (nil? view) (log/d "view is nil"))
              (if (nil? id) (log/d "id is nil"))

                        ; final View v = viewPager.getChildAt(i);
                        ; if (v == null)
                        ;     continue;

                        ; // reveal correct child position
                        ; Integer tag = (Integer)v.getTag();
                        ; if (tag == null)
                        ;     continue;

                      (cond
                        (= id 0) (set-page-content view (dec (getfield this :global-pos)))
                        (= id 1) (set-page-content view (getfield this :global-pos))
                        (= id 2) (set-page-content view (inc (getfield this :global-pos)))
                        ; switch (tag.intValue())
                        ; {
                        ;     case 0:
                        ;         setPageContent(v, globalPosition - 1);
                        ;         break;
                        ;     case 1:
                        ;         setPageContent(v, globalPosition);
                        ;         break;
                        ;     case 2:
                        ;         setPageContent(v, globalPosition + 1);
                        ;         break;
                        ; }
                        )
                        )
                      )
      


        );let

    ;TODO shuffle the views
    (.setCurrentItem (getfield this :vp) 1 false))
    )
  )

(defn fragment-onPageScrolled [this position positionOffset positionOffsetPixels]
  ; (log/d "fragment-onPageScrolled")
  )

(defn fragment-onPageSelected [this position]
  (let [old (getfield this :focused-page)]
    (setfield this :focused-page position)
  (log/d "fragment-onPageSelected, " old " -> " position)
  ))
