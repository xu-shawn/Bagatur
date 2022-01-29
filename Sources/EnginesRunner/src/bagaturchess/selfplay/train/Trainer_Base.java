package bagaturchess.selfplay.train;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.bitboard.common.GlobalConstants;
import bagaturchess.bitboard.impl.Constants;

import bagaturchess.deeplearning.ActivationFunction;
import bagaturchess.search.api.IEvalConfig;
import bagaturchess.search.api.IEvaluator;
import bagaturchess.search.api.IEvaluatorFactory;
import bagaturchess.search.impl.env.SharedData;


public class Trainer_Base implements Trainer {
	
	
	private static final float MAX_EVAL 							= 7777;
	
	private static final int UPDATE_FREQUENCY_GAMES_COUNT 			= 100;
	
	
	//private static final float LAMBDA 							= 0.9833f;
	private static final double LAMBDA 								= 0.991f;
	
	private static final Map<Integer, double[]> LAMBDAS = new HashMap<Integer, double[]>();
	
	
	static {
		
		for (int moves_count = 1; moves_count < GlobalConstants.MAX_MOVES_IN_GAME; moves_count++) {
			
			double[] CURRENT_LAMBDAS = new double[moves_count];
			
			double current_lambda = LAMBDA;
			
			for (int i = CURRENT_LAMBDAS.length - 1; i >= 0; i--) {
				
				CURRENT_LAMBDAS[i] = current_lambda;
				
				current_lambda *= LAMBDA;
			}
			
			LAMBDAS.put(moves_count, CURRENT_LAMBDAS);
			
			
			if (moves_count == 60) {
				
				for (int i = CURRENT_LAMBDAS.length - 1; i >= 0; i--) {
					
					System.out.println("Trainer_GOLDENMIDDLE.CURRENT_LAMBDAS[60]: move " + i + " = " + CURRENT_LAMBDAS[i]);
				}
			}
		}
	}
	
	
	//Members
	protected IBitBoard bitboard;
	
	protected String filename_NN;
	
	protected List<Object> inputs_per_move;
	protected List<Float> outputs_per_move_actual;
	protected List<Float> outputs_per_move_expected;
	
	private int update_counter = UPDATE_FREQUENCY_GAMES_COUNT;
	
	protected ActivationFunction activation_function;
	
	private IEvalConfig evalConfig;
	
	//These member have to be recreated after each Epoch in order to read the last weights
	private IEvaluator evaluator;
	
	
	public Trainer_Base(IBitBoard _bitboard, String _filename_NN, IEvalConfig _evalConfig, ActivationFunction _activation_function) throws Exception {
		
		bitboard = _bitboard;
		
		filename_NN = _filename_NN;
		
		evalConfig = _evalConfig;
		
		activation_function = _activation_function;
		
		if (!(new File(filename_NN)).exists()) {
			
			throw new IllegalStateException("NN file not found: " + filename_NN);
			
		}
		
		inputs_per_move 				= new ArrayList<Object>(); 
		
		outputs_per_move_actual 		= new ArrayList<Float>();
		
		outputs_per_move_expected 		= new ArrayList<Float>();
				
		reloadFromFile();
	}
	
	
	protected void updateWeights() throws Exception {
		
		update_counter = UPDATE_FREQUENCY_GAMES_COUNT;
	}
	
	
	protected void reloadFromFile() throws Exception {
		
		String className = evalConfig.getEvaluatorFactoryClassName();
			
		IEvaluatorFactory evaluator_factory = (IEvaluatorFactory) SharedData.class.getClassLoader().loadClass(className).newInstance();
		
		evaluator = evaluator_factory.create(bitboard, null, evalConfig);
	}
	
	
	@Override
	public void newGame() {
		
		inputs_per_move.clear();
		
		outputs_per_move_actual.clear();
		
		outputs_per_move_expected.clear();
	}
	
	
	@Override
	public void addBoardPosition(IBitBoard bitboard) {		
		
		double actual_eval = evaluator.fullEval(0, IEvaluator.MIN_EVAL, IEvaluator.MAX_EVAL, bitboard.getColourToMove());
		
		if (bitboard.getColourToMove() == Constants.COLOUR_BLACK) {
			
			actual_eval = -actual_eval;
		}
		
		outputs_per_move_actual.add((float) actual_eval);
	}
	
	
	@Override
	public void setGameOutcome(float game_result) throws Exception {
		
		if (inputs_per_move.size() == 0) {
			
			System.out.println("Game with no moves");
			
			return;
		}
		
		setGameOutcome_Lambda(game_result);
		
		backwardView();
	}
	
	
	private void setGameOutcome_Lambda(float game_result) {
		
		float final_eval;
		
		if (game_result == 0) { //Draw
			
			final_eval = 0;
					
		} else if (game_result == 1) { //White wins
			
			final_eval = activation_function.gety(MAX_EVAL);
			
		} else { //Black wins
			
			final_eval = activation_function.gety(-MAX_EVAL);
		}
		
		double[] LAMBDAS_ARRAY = LAMBDAS.get(inputs_per_move.size());
		
		for (int i = 0; i < inputs_per_move.size(); i++) {
			
	        outputs_per_move_expected.add((float) (LAMBDAS_ARRAY[i] * final_eval));
		}
	}
	
	
	public void backwardView() throws Exception {		
		
		
		if (inputs_per_move.size() != outputs_per_move_actual.size()) {
			
			throw new IllegalStateException();
		}
		
		if (outputs_per_move_actual.size() != outputs_per_move_expected.size()) {
			
			throw new IllegalStateException();
		}
		
		
		update_counter--;
		
		if (update_counter <= 0) {
			
			updateWeights();
		}
	}
	
	
	private void setGameOutcome_Linear(float game_result) {
		
		float step;
		
		if (game_result == 0) { //Draw
			
			step = 0;
					
		} else if (game_result == 1) { //White wins
			
			//step = (activation_function.gety(IEvaluator.MAX_EVAL) / (float) inputs_per_move.size());
			step = (activation_function.gety(MAX_EVAL) / (float) inputs_per_move.size());
			
		} else { //Black wins
			
			//step = (activation_function.gety(IEvaluator.MIN_EVAL) / (float) inputs_per_move.size());
			step = (activation_function.gety(-MAX_EVAL) / (float) inputs_per_move.size());
		}
		
		for (int i = 0; i < inputs_per_move.size(); i++) {
	        
	        float output = i * step;
	        
	        outputs_per_move_expected.add(output);
		}
	}
	
	
	/*public void setGameOutcome_Sigmoid(float game_result) {
	
		boolean draw = game_result == 0;
		
		boolean white_win = game_result == 1;
		
		float step;
		
		if (draw) {
			
			step = 0;
					
		} else if (white_win) {
			
			step = +(float) ((activation_function.gety(IEvaluator.MAX_EVAL) - 0.5) / (float) inputs_per_move.size());
			
		} else {
			
			step = -(float) ((0.5 - activation_function.gety(IEvaluator.MIN_EVAL)) / (float) inputs_per_move.size());
		}
		
		for (int i = 0; i < inputs_per_move.size(); i++) {
			
	        float[] output = new float[1];
	        
	        output[0] = (float) (0.5 + i * step);
	        
	        dataset.addItem(inputs_per_move.get(i), output);
		}
	}*/
}
