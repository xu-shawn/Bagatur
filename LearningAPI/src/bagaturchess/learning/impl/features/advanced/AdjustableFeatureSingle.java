

package bagaturchess.learning.impl.features.advanced;


import bagaturchess.bitboard.impl.utils.StringUtils;
import bagaturchess.learning.api.ISignal;
import bagaturchess.learning.impl.features.baseimpl.Weight;
import bagaturchess.learning.impl.signals.SingleSignal;


public class AdjustableFeatureSingle extends AdjustableFeature {
	
	
	private static final long serialVersionUID = -861041671370138696L;
	
	
	private Weight weight;
	
	
	public AdjustableFeatureSingle(int _id, String _name, int _complexity,
			double _omin, double _omax, double oinitial,
			double dummy_value1, double dummy_value2, double dummy_value3) {
		
		super(_id, _name, _complexity);
		
		weight = new Weight(_omin, _omax, oinitial);
	}
	
	
	public ISignal createNewSignal() {
		return new SingleSignal();
	}
	
	
	protected void merge(AdjustableFeature other) {
		
		if (other instanceof AdjustableFeatureSingle) {
			
			AdjustableFeatureSingle other_fs = (AdjustableFeatureSingle) other;
			
			weight.merge(other_fs.weight);
			
			if (!other.getName().equals(getName())) {
				
				throw new IllegalStateException("Feature names not equals");
			}
		}
	}
	
	
	@Override
	public void clear() {
		weight.clear();
	}
	
	
	@Override
	public void applyChanges() {
		
		weight.multiplyCurrentWeightByAmountAndDirection();
		
		/*if (getId() == 5) {
			
			System.out.println("QUEEN.MATERIAL=" + weight.getWeight());
		}*/
	}
	
	
	public void adjust(ISignal signal, double amount, double dummy_value) {
		
		if (signal.getStrength() < 0) {
			
			amount = -amount;
		}
		
		weight.adjust(amount);
	}
	
	
	public double eval(ISignal signal, double dummy_value) {
		
		return weight.getWeight() * signal.getStrength();
	}
	
	
	@Override
	public double getWeight() {
		
		return weight.getWeight();
	}
	
	
	@Override
	public double getWeight(int index) {

		throw new UnsupportedOperationException();
	}
	
	
	@Override
	public double getLearningSpeed() {
		
		throw new UnsupportedOperationException();
	}
	
	
	@Override
	public String toString() {
		
		String result = "";
		
		result += "FEATURE " + StringUtils.fill("" + getId(), 3) + " "
					+ StringUtils.fill(getName(), 20)
					+ weight;
		
		return result;
	}


	@Override
	public String toJavaCode(String suffix) {
		
		return "public static final double " + getName().replace('.', '_') + suffix + "	=	"
				+ (weight.getWeight()) + ";" + "\r\n";
		
	}
}
