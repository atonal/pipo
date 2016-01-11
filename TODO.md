# TODO

* Update UI on button press, handle the db and state in background. Adjust UI if something goes wrong.
* Use selectors in year/week dialog. Number of weeks automatically based on year.
* Add option to select the week based on a specific date in the dialog
* Clock view to display currently ongoing work. Hint: broadcastReceiver and Intent.ACTION_TIME_TICK
* Service to punch in/out automatically based on GPS
* Move between weeks by swiping left/right
* Split work at midnight automatically
* Use local time in all places
* Add state for start/stop GPS
* Automaticallu subtract lunch break, if work day is long enough (6h)
* Put location updates into a separate thread inside the service
* Add state and watch for it in main, if service updates it in the background
