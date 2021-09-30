package eu.h2020.helios_social.core.messagingtest;

import android.os.Build;
import android.text.TextUtils;

public class VersionUtils {
        /**
     * Get current Android version data. This based on an example from:
     *   https://stackoverflow.com/questions/3093365/how-can-i-check-the-system-version-of-android?rq=1
     */
    public static String getAndroidVersion() {
        double release=Double.parseDouble(Build.VERSION.RELEASE.replaceAll("(\\d+[.]\\d+)(.*)","$1"));
        String codeName="Unsupported";
        if (release < 4.1) {
            return codeName;
        }
        if ((release >= 4.1 && release < 4.4)) {
            codeName = "Jelly Bean";
            return codeName;
        }
        switch ((int)release) {
            case 4:
                codeName = "Kit Kat";
                break;
            case 5:
                codeName = "Lollipop";
                break;
            case 6:
                codeName = "Marshmallow";
                break;
            case 7:
                codeName = "Nougat";
                break;
            case 8:
                codeName = "Oreo";
                break;
            case 9:
                codeName = "Pie";
                break;
            default:
                codeName = "Android " + ((int)release);
                break;
        }
        return codeName+" v"+release+", API Level: " + Build.VERSION.SDK_INT;
    }

    /**
     * Returns the consumer friendly device name.
     * This is from:
     *   https://stackoverflow.com/questions/1995439/get-android-phone-model-programmatically-how-to-get-device-name-and-model-prog
     */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }
}
