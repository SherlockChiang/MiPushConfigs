import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

class CheckPatterns {
    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 17) throw new IllegalStateException("Use JDK 17");
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isEmpty()) continue;
                Pattern.compile(new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8));
                count++;
            }
        }
        System.out.println(count + " distinct Java regexes compiled successfully on JDK 17");
    }
}
