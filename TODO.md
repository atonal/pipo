# TODO

* Update UI on button press, handle the db and state in background. Adjust UI if something goes wrong.
* Use selectors in year/week dialog. Number of weeks automatically based on year.
* Add option to select the week based on a specific date in the dialog
* Clock view to display currently ongoing work. Hint: broadcastReceiver and Intent.ACTION_TIME_TICK
* Move between weeks by swiping left/right
* Split work at midnight automatically
* Use local time in all places
* Add state for start/stop GPS
* Add automatic lunch break if _total_ work hours by day is more than 6h?
* Put location updates into a separate thread inside the service
* Setup target location by drawing onto a map
* Play a sound and/or vibrate on punches
* Better detailed punch view, where user can manually edit the punches
* Adjust GPS query frequency based on time of day
* Represent in/out state in service's status bar icon
