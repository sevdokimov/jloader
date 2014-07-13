import ru.testdata.Fff;
import ru.testdata.Rrr;

/**
 * @author Sergey Evdokimov
 */
public final class Zzz {

    public Rrr getRrr() {
        return null;
    }

    public static String hello() {
        if (new Zzz().getRrr() == null) {
            Fff f = new Fff();
            return f.fff() == 1 ? "correct" : null;
        }

        return null;
    }

}
