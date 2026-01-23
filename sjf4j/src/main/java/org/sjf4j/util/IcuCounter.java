package org.sjf4j.util;

import com.ibm.icu.text.BreakIterator;
import java.util.Locale;


final class IcuCounter {

    private static final ThreadLocal<BreakIterator> TL =
            ThreadLocal.withInitial(() -> BreakIterator.getCharacterInstance(Locale.ROOT));

    static int count(String s) {
        BreakIterator it = TL.get();
        it.setText(s);

        int count = 0;
        int i = it.first();
        for (int j = it.next();
             j != BreakIterator.DONE;
             i = j, j = it.next()) {
            count++;
        }
        return count;
    }

    private IcuCounter() {}
}
