import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

class FindMyPhoneView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var app = Application.getApp() as FindMyPhoneApp;

        var text;
        var color = Graphics.COLOR_WHITE;
        if (app.status == STATUS_SENDING) {
            text = "Sending...";
        } else if (app.status == STATUS_RINGING) {
            text = "Phone ringing\nSELECT to stop";
            color = Graphics.COLOR_GREEN;
        } else if (app.status == STATUS_ERROR) {
            text = app.errorText + "\nSELECT to retry";
            color = Graphics.COLOR_RED;
        } else {
            text = "SELECT to\nring phone";
        }

        dc.setColor(color, Graphics.COLOR_BLACK);
        dc.clear();
        dc.drawText(dc.getWidth() / 2, dc.getHeight() / 2, Graphics.FONT_MEDIUM,
                    text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
