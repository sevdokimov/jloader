package com.ess.jloader.packer.dictionary;

import com.ess.jloader.packer.CountMap;
import com.ess.jloader.utils.ByteArrayString;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.google.common.base.Throwables;
import com.google.common.collect.Ordering;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;

/**
 * @author Sergey Evdokimov
 */
public class DictionaryCalculator {

    private static final int W_SIZE = 1024*32;
    private static final int W_SIZE_MASK = W_SIZE - 1;

    private final byte[] window = new byte[W_SIZE];

    private int windowPos = 0;

    private CountMap<ByteArrayString> countMap = new CountMap<ByteArrayString>();

    private DictionaryCalculator() {
    }

    private void write(byte[] data, int pos, int end) {
        byte[] window = this.window;
        int windowPos = this.windowPos;

        while (pos < end) {
            byte firstB = data[pos];

            int maxLength = 0;

            for (int i = 0; i < window.length; i++) {
                if (firstB == window[i]) {
                    int dataI = pos;
                    int winI = i;

                    do {
                        dataI++;
                        winI = (winI + 1) & W_SIZE_MASK;
                    } while (dataI < end && winI != windowPos && window[winI] == data[dataI]);

                    int len = dataI - pos;
                    if (len > maxLength) {
                        maxLength = len;
                    }
                }
            }

            if (maxLength == 0) {
                maxLength = 1;
            }

            if (maxLength > 2) {
                ByteArrayString str = new ByteArrayString(data, pos, maxLength);
                countMap.incrementAndGet(str);
            }

            for (int i = 0; i < maxLength; i++) {
                window[windowPos] = data[pos + i];
                windowPos = (windowPos + 1) & W_SIZE_MASK;
            }

            pos += maxLength;
        }

        this.windowPos = windowPos;
    }

    private byte[] getDictionary() {
//            Map<ByteArrayString, Long> filteredMap = Maps.filterValues(countMap.asMap(), new Predicate<Long>() {
//                @Override
//                public boolean apply(Long aLong) {
//                    return aLong > 1;
//                }
//            });

        ByteArrayString[] strings = countMap.keySet().toArray(new ByteArrayString[countMap.size()]);
        Arrays.sort(strings, Ordering.from(new Comparator<ByteArrayString>() {
            @Override
            public int compare(ByteArrayString o1, ByteArrayString o2) {
                return Long.compare(countMap.get(o1), countMap.get(o2));
            }
        }).reverse());

        int dictionarySize = 0;
        LinkedHashSet<ByteArrayString> usedInDictionaryStrings = new LinkedHashSet<ByteArrayString>();

        for (ByteArrayString s : strings) {
            if (dictionarySize + s.getLength() > 1024 * 4) {
                break;
            }

            dictionarySize += s.getLength();
            usedInDictionaryStrings.add(s);
        }

        Arrays.sort(strings, Ordering.from(new Comparator<ByteArrayString>() {
            @Override
            public int compare(ByteArrayString o1, ByteArrayString o2) {
                return Long.compare(o1.getLength() * countMap.get(o1), o2.getLength() * countMap.get(o2));
            }
        }).reverse());

        for (ByteArrayString s : strings) {
            if (!usedInDictionaryStrings.contains(s)) {
                if (dictionarySize + s.getLength() > 1024 * 27) {
                    break;
                }

                dictionarySize += s.getLength();
                usedInDictionaryStrings.add(s);
            }
        }

        OpenByteOutputStream stream = new OpenByteOutputStream(dictionarySize);

        ByteArrayString[] rrr = usedInDictionaryStrings.toArray(new ByteArrayString[usedInDictionaryStrings.size()]);

        for (int i = rrr.length; --i >= 0; ) {
            try {
                rrr[i].writeTo(stream);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        byte[] res = stream.getBuffer();

        assert res.length == dictionarySize;
        assert stream.size() == dictionarySize;

        return res;
    }

    public static byte[] buildDictionary(Collection<OpenByteOutputStream> data) {
        DictionaryCalculator dOut = new DictionaryCalculator();

        for (OpenByteOutputStream openByteOutputStream : data) {
            dOut.write(openByteOutputStream.getBuffer(), 0, Math.min(openByteOutputStream.size(), 1024));
        }

        return dOut.getDictionary();
    }

}
