/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl.directed.inference;

import java.util.Vector;

import probcog.bayesnets.inference.ITimeLimitedInference;
import probcog.bayesnets.inference.SampledDistribution;


public class TimeLimitedInference extends probcog.bayesnets.inference.TimeLimitedInference {

	Sampler inference;
	
	public TimeLimitedInference(ITimeLimitedInference inference, double time, double interval) throws Exception {
		super(inference, time, interval);
		this.inference = (Sampler)inference;
	}
	
	@Override
	protected void printResults(SampledDistribution dist) {	
		inference.printResults(dist);
	}
	
	public Vector<InferenceResult> getResults(SampledDistribution dist) {
		return inference.getResults(dist);
	}
}