//package charg.ing.stations.util;
//
//import java.util.regex.Pattern;
//
//public class EmailValidator {
//    private static final Pattern EMAIL_PATTERN = Pattern.compile(
//            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
//            Pattern.CASE_INSENSITIVE
//    );
//
//    public static boolean isValid(String email) {
//        return email != null && EMAIL_PATTERN.matcher(email).matches();
//    }
//}