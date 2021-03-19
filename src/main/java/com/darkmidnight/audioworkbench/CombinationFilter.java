package com.darkmidnight.audioworkbench;

import java.util.ArrayList;
import java.util.List;

/**
 * This was implemented with the idea that a filter consisting of several bandpass filters (i.e. allow 300-500, and 750-1000Hz)
 * might be useful as a means of uniquely identifying the sound.
 * Hasn't actually been used for more than one, but I see potential for it being used later, and it provides a decent structure
 * for the JSON, so might as well keep it.
 * @author anthony
 */
public class CombinationFilter {

    List<BandPassFilter> filters;

    public CombinationFilter() {
        this.filters = new ArrayList<>();
    }

    public List<BandPassFilter> getFilters() {
        return filters;
    }

    public void addFilter(double start, double end, double threshold) {
        boolean processed = false;
        for (BandPassFilter existing : filters) {
            if (existing.getStart() >= start || existing.getStart() <= end
                    || (existing.getEnd() >= start || existing.getEnd() <= end)) {
                System.out.println("This starts or ends in the middle of an existing filter. Joining (threshold will be averaged");
                start = (start < existing.getStart() ? start : existing.getStart());
                end = (end > existing.getEnd() ? end : existing.getEnd());
                threshold = (threshold + existing.getThreshold()) / 2;
                existing.setStart(start);
                existing.setEnd(end);
                existing.setThreshold(threshold);
                processed = true;
            }
        }
        if (!processed) {
            BandPassFilter bpf = new BandPassFilter(start, end, threshold);
            filters.add(bpf);
        }
    }

    @Override
    public String toString() {

        StringBuffer sb = new StringBuffer();
        sb.append("CombinationFilter{" + "filters=");
        for (BandPassFilter existing : filters) {
            sb.append(existing.toString());
        }
        sb.append('}');
        return sb.toString();
    }

    public static class BandPassFilter {

        double start;
        double end;
        double threshold;

        public BandPassFilter(double start, double end, double threshold) {
            this.start = start;
            this.end = end;
            this.threshold = threshold;
        }

        public BandPassFilter() {
        }

        public double getStart() {
            return start;
        }

        public void setStart(double start) {
            this.start = start;
        }

        public double getEnd() {
            return end;
        }

        public void setEnd(double end) {
            this.end = end;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        @Override
        public String toString() {
            return "BandPassFilter{" + "start=" + start + ", end=" + end + ", threshold=" + threshold + '}';
        }

    }
}
