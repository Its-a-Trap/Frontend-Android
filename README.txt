It's A Trap is an alternate reality game based around placing traps and having other users walk into them. A user can plant traps anywhere in the world, and whenever a user moves over someone else' trap (as determined by GPS) the trap is triggered and disappears. Points are awarded for having other users walk over your traps, and subtracted for walking over others' traps. Users can also sparingly use a short-range sweep ability which reveals nearby traps placed by others and see high scores of nearby players. This repository contains the Android version of the app.

The core functionality of the app is in place, but some elements remain unimplemented.

- Our push notification infrastructure is primitive - it simply notifies the app that it should refresh all data from the server, rather than which specific pieces of information to update.

- We were unable to implement the user dying if the app is turned off. Android documentation indicated that not all versions actually call onDestroy before ending an activity, so we had no reliable way to determine when the app was being closed.

- We did not implement either Twitter or Facebook login mechanisms as originally planned. Facebook's and Twitter's were not documented for use with Android Studio.

- We do not handle the situation of losing location data or network connectivity. The app should not crash in these situations, but it also doesn't do anything intelligent, which might allow players to gain advantage by disabling network or location.

- We have not fine-tuned the location update frequency. Currently we perform updates whenever the phone senses it moves 1m at a maximum rate of once per second. This is much higher than recommended levels and seems to drain phone batteries extremely rapidly.

- We intended to implement a different place and replace gesture. Instead of a two-tap system we wanted a long-press gesture during which the trap would inflate to full size while the user pressed. This was scrapped due to complexity.

The app should run without any particular effort on your part. However, you will need to run it on an actual device as the google maps api will not work under an emulator.