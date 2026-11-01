import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

class FindMyPhoneDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        (Application.getApp() as FindMyPhoneApp).toggleRing();
        return true;
    }
}
