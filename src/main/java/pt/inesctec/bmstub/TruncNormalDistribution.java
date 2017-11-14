package pt.inesctec.bmstub;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;

public class TruncNormalDistribution extends NormalDistribution {

    public TruncNormalDistribution(RandomGenerator rng, double mean, double sd) {
        super(rng, mean, sd);
    }

    @Override
    public double sample() {
        double s = super.sample();
        if (s<0)
            s = 0;
        return s;
    }
}
