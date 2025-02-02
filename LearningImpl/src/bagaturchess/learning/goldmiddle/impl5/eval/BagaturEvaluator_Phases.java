package bagaturchess.learning.goldmiddle.impl5.eval;


import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.search.api.IEvalConfig;
import bagaturchess.search.impl.eval.BaseEvaluator;
import bagaturchess.search.impl.eval.cache.IEvalCache;


public class BagaturEvaluator_Phases extends BaseEvaluator {
	
	
	private Evaluator evaluator;
	
	
	public BagaturEvaluator_Phases(IBitBoard _bitboard, IEvalCache _evalCache, IEvalConfig _evalConfig) {
		
		super(_bitboard, _evalCache, _evalConfig);
		
		evaluator = new Evaluator(_bitboard);
	}


	/* (non-Javadoc)
	 * @see bagaturchess.search.impl.eval.BaseEvaluator#phase1()
	 */
	@Override
	protected int phase1() {
		int eval = 0;
		
		eval = (int) evaluator.calculateScore1();
		
		return eval;
	}
	
	
	/* (non-Javadoc)
	 * @see bagaturchess.search.impl.eval.BaseEvaluator#phase2()
	 */
	@Override
	protected int phase2() {
		int eval = 0;
		
		eval = (int) evaluator.calculateScore2();
		
		return eval;
	}
	
	
	/* (non-Javadoc)
	 * @see bagaturchess.search.impl.eval.BaseEvaluator#phase3()
	 */
	@Override
	protected int phase3() {
		int eval = 0;
		
		return eval;
	}
	
	
	/* (non-Javadoc)
	 * @see bagaturchess.search.impl.eval.BaseEvaluator#phase4()
	 */
	@Override
	protected int phase4() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
	/* (non-Javadoc)
	 * @see bagaturchess.search.impl.eval.BaseEvaluator#phase5()
	 */
	@Override
	protected int phase5() {
		// TODO Auto-generated method stub
		return 0;
	}
}
