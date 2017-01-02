/**********************************************************************************************************************
 * garbagecat                                                                                                         *
 *                                                                                                                    *
 * Copyright (c) 2008-2016 Red Hat, Inc.                                                                              *
 *                                                                                                                    * 
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse *
 * Public License v1.0 which accompanies this distribution, and is available at                                       *
 * http://www.eclipse.org/legal/epl-v10.html.                                                                         *
 *                                                                                                                    *
 * Contributors:                                                                                                      *
 *    Red Hat, Inc. - initial API and implementation                                                                  *
 *********************************************************************************************************************/
package org.eclipselabs.garbagecat.domain.jdk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipselabs.garbagecat.domain.BlockingEvent;
import org.eclipselabs.garbagecat.domain.CombinedData;
import org.eclipselabs.garbagecat.domain.TriggerData;
import org.eclipselabs.garbagecat.util.jdk.JdkMath;
import org.eclipselabs.garbagecat.util.jdk.JdkRegEx;
import org.eclipselabs.garbagecat.util.jdk.JdkUtil;

/**
 * <p>
 * G1_MIXED_PAUSE
 * </p>
 * 
 * <p>
 * G1 mixed generation collection. Performed at the same time as a young collection, so it is a collection of the young
 * space and the low liveness regions of the old space. The <code>-XX:InitiatingHeapOccupancyPercent</code> defines the
 * heap occupancy threshold for initiating a mixed pause.
 * </p>
 * 
 * <h3>Example Logging</h3>
 * 
 * <p>
 * 1) Standard format:
 * </p>
 * 
 * <pre>
 * 1.305: [GC pause (mixed) 102M-&gt;24M(512M), 0.0254200 secs]
 * </pre>
 * 
 * <p>
 * 2) With -XX:+PrintGCDateStamps:
 * </p>
 * 
 * <pre>
 * 2010-02-26T08:31:51.990-0600: [GC pause (mixed) 102M-&gt;24M(512M), 0.0254200 secs]
 * </pre>
 * 
 * <p>
 * 3) After {@link org.eclipselabs.garbagecat.preprocess.jdk.G1PreprocessAction} with trigger:
 * </p>
 * 
 * <pre>
 * 2973.338: [GC pause (G1 Evacuation Pause) (mixed), 0.0457502 secs][Eden: 112.0M(112.0M)-&gt;0.0B(112.0M) Survivors: 16.0M-&gt;16.0M Heap: 12.9G(30.0G)-&gt;11.3G(30.0G)] [Times: user=0.19 sys=0.00, real=0.05 secs]
 * </pre>
 * 
 * <p>
 * 4) After {@link org.eclipselabs.garbagecat.preprocess.jdk.G1PreprocessAction} without trigger:
 * </p>
 * 
 * <pre>
 * 3082.652: [GC pause (mixed), 0.0762060 secs] 12083M-&gt;9058M(26624M) [Times: user=0.30 sys=0.00, real=0.08 secs]
 * </pre>
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * @author James Livingston
 * 
 */
public class G1MixedPauseEvent extends G1Collector implements BlockingEvent, CombinedData, TriggerData {

    /**
     * Trigger(s) regular expression(s).
     */
    private static final String TRIGGER = "(" + JdkRegEx.TRIGGER_G1_EVACUATION_PAUSE + "|"
            + JdkRegEx.TRIGGER_TO_SPACE_EXHAUSTED + ")";

    /**
     * Regular expression standard format.
     */
    private static final String REGEX = "^" + JdkRegEx.TIMESTAMP + ": \\[GC pause \\(mixed\\) " + JdkRegEx.SIZE_G1_WHOLE
            + "->" + JdkRegEx.SIZE_G1_WHOLE + "\\(" + JdkRegEx.SIZE_G1_WHOLE + "\\), " + JdkRegEx.DURATION + "\\]"
            + JdkRegEx.TIMES_BLOCK + "?[ ]*$";

    /**
     * Regular expression preprocessed.
     */
    private static final String REGEX_PREPROCESSED = "^" + JdkRegEx.TIMESTAMP + ": \\[GC pause( \\(" + TRIGGER
            + "\\))? \\(mixed\\)( \\(" + TRIGGER + "\\))?, " + JdkRegEx.DURATION + "\\]\\[Eden: "
            + JdkRegEx.SIZE_G1_DECIMAL + "\\(" + JdkRegEx.SIZE_G1_DECIMAL + "\\)->" + JdkRegEx.SIZE_G1_DECIMAL + "\\("
            + JdkRegEx.SIZE_G1_DECIMAL + "\\) Survivors: " + JdkRegEx.SIZE_G1_DECIMAL + "->" + JdkRegEx.SIZE_G1_DECIMAL
            + " Heap: " + JdkRegEx.SIZE_G1_DECIMAL + "\\(" + JdkRegEx.SIZE_G1_DECIMAL + "\\)->"
            + JdkRegEx.SIZE_G1_DECIMAL + "\\(" + JdkRegEx.SIZE_G1_DECIMAL + "\\)\\]" + JdkRegEx.TIMES_BLOCK + "?[ ]*$";

    /**
     * The log entry for the event. Can be used for debugging purposes.
     */
    private String logEntry;

    /**
     * The elapsed clock time for the GC event in milliseconds (rounded).
     */
    private int duration;

    /**
     * The time when the GC event happened in milliseconds after JVM startup.
     */
    private long timestamp;

    /**
     * Combined generation size (kilobytes) at beginning of GC event.
     */
    private int combined;

    /**
     * Combined generation size (kilobytes) at end of GC event.
     */
    private int combinedEnd;

    /**
     * Available space in multiple generation (kilobytes).
     */
    private int combinedAvailable;

    /**
     * The trigger for the GC event.
     */
    private String trigger;

    /**
     * Create event from log entry.
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public G1MixedPauseEvent(String logEntry) {
        this.logEntry = logEntry;
        if (logEntry.matches(REGEX)) {
            // standard format
            Pattern pattern = Pattern.compile(REGEX);
            Matcher matcher = pattern.matcher(logEntry);
            if (matcher.find()) {
                timestamp = JdkMath.convertSecsToMillis(matcher.group(1)).longValue();
                combined = JdkMath.calcKilobytes(Integer.parseInt(matcher.group(2)), matcher.group(3).charAt(0));
                combinedEnd = JdkMath.calcKilobytes(Integer.parseInt(matcher.group(4)), matcher.group(5).charAt(0));
                combinedAvailable = JdkMath.calcKilobytes(Integer.parseInt(matcher.group(6)),
                        matcher.group(7).charAt(0));
                duration = JdkMath.convertSecsToMillis(matcher.group(8)).intValue();
            }
        } else if (logEntry.matches(REGEX_PREPROCESSED)) {
            // preprocessed format
            Pattern pattern = Pattern.compile(REGEX_PREPROCESSED);
            Matcher matcher = pattern.matcher(logEntry);
            if (matcher.find()) {
                timestamp = JdkMath.convertSecsToMillis(matcher.group(1)).longValue();
                // use last trigger
                if (matcher.group(5) != null) {
                    trigger = matcher.group(5);
                } else if (matcher.group(3) != null) {
                    trigger = matcher.group(3);
                }
                duration = JdkMath.convertSecsToMillis(matcher.group(6)).intValue();
                combined = JdkMath.convertSizeG1DetailsToKilobytes((matcher.group(19)), matcher.group(20).charAt(0));
                combinedEnd = JdkMath.convertSizeG1DetailsToKilobytes((matcher.group(23)), matcher.group(24).charAt(0));
                combinedAvailable = JdkMath.convertSizeG1DetailsToKilobytes((matcher.group(25)),
                        matcher.group(26).charAt(0));
            }
        }
    }

    /**
     * Alternate constructor. Create detail logging event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event happened in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in milliseconds.
     */
    public G1MixedPauseEvent(String logEntry, long timestamp, int duration) {
        this.logEntry = logEntry;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String getLogEntry() {
        return logEntry;
    }

    public int getDuration() {
        return duration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getCombinedOccupancyInit() {
        return combined;
    }

    public int getCombinedOccupancyEnd() {
        return combinedEnd;
    }

    public int getCombinedSpace() {
        return combinedAvailable;
    }

    public String getName() {
        return JdkUtil.LogEventType.G1_MIXED_PAUSE.toString();
    }

    public String getTrigger() {
        return trigger;
    }

    /**
     * Determine if the logLine matches the logging pattern(s) for this event.
     * 
     * @param logLine
     *            The log line to test.
     * @return true if the log line matches the event pattern, false otherwise.
     */
    public static final boolean match(String logLine) {
        return logLine.matches(REGEX) || logLine.matches(REGEX_PREPROCESSED);
    }
}
