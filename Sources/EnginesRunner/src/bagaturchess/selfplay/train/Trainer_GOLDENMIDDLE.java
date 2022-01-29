/**
 *  BagaturChess (UCI chess engine and tools)
 *  Copyright (C) 2005 Krasimir I. Topchiyski (k_topchiyski@yahoo.com)
 *  
 *  This file is part of BagaturChess program.
 * 
 *  BagaturChess is open software: you can redistribute it and/or modify
 *  it under the terms of the Eclipse Public License version 1.0 as published by
 *  the Eclipse Foundation.
 *
 *  BagaturChess is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Eclipse Public License for more details.
 *
 *  You should have received a copy of the Eclipse Public License version 1.0
 *  along with BagaturChess. If not, see http://www.eclipse.org/legal/epl-v10.html
 *
 */
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
import bagaturchess.learning.api.IAdjustableFeature;
import bagaturchess.learning.api.IFeature;
import bagaturchess.learning.api.ISignal;
import bagaturchess.learning.api.ISignalFiller;
import bagaturchess.learning.api.ISignals;
import bagaturchess.learning.goldmiddle.api.ILearningInput;
import bagaturchess.learning.goldmiddle.api.LearningInputFactory;
import bagaturchess.learning.goldmiddle.impl4.eval.BagaturEvaluatorFactory_GOLDENMIDDLE;
import bagaturchess.learning.goldmiddle.impl4.filler.Bagatur_V20_FeaturesConfigurationImpl_0Initially;
import bagaturchess.learning.goldmiddle.impl4.filler.Bagatur_V20_SignalFiller;
import bagaturchess.learning.impl.features.baseimpl.Features_Splitter;
import bagaturchess.learning.impl.signals.Signals;
import bagaturchess.search.api.IEvaluator;


public class Trainer_GOLDENMIDDLE implements Trainer {
	
	
	private static final ActivationFunction activation_function = ActivationFunction.LINEAR;
	
	private static final float MAX_EVAL 						= 7777;
	
	private static final int UPDATE_FREQUENCY_GAMES_COUNT 		= 100;
	
	//private static final float LAMBDA 							= 0.9833f;
	private static final double LAMBDA 							= 0.99999997f;
	
	private static final Map<Integer, double[]> LAMBDAS = new HashMap<Integer, double[]>();
	
	
	static {
		
		for (int moves_count = 1; moves_count < GlobalConstants.MAX_MOVES_IN_GAME; moves_count++) {
			
			double[] CURRENT_LAMBDAS = new double[moves_count];
			
			double current_lambda = LAMBDA;
			
			for (int i = CURRENT_LAMBDAS.length - 1; i >= 0; i--) {
				
				CURRENT_LAMBDAS[i] = current_lambda;
				
				//current_lambda *= current_lambda;
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
	private IBitBoard bitboard;
	
	private String filename_NN;
	
	private List<ISignals> inputs_per_move;
	private List<IFeature[]> features_per_move_for_update;
	private List<Float> outputs_per_move_actual;
	private List<Float> outputs_per_move_expected;
	
	private long stats_count_weights_changes;
	private long stats_sum_weights_changes;
	private long stats_sum_deltaP;
	
	private int update_counter = UPDATE_FREQUENCY_GAMES_COUNT;
	
	
	//These members have to be recreated after each Epoch in order to read the last weights
	private ILearningInput input;
	private ISignalFiller filler;
	private Features_Splitter features_splitter;
	private IEvaluator evaluator;
	
	
	public Trainer_GOLDENMIDDLE(IBitBoard _bitboard, String _filename_NN) throws Exception {
		
		bitboard = _bitboard;
		
		filename_NN = _filename_NN;
		
		if (!(new File(filename_NN)).exists()) {
			
			throw new IllegalStateException("NN file not found: " + filename_NN);
			
		}
		
		inputs_per_move 				= new ArrayList<ISignals>();
		
		features_per_move_for_update 	= new ArrayList<IFeature[]>();
		
		outputs_per_move_actual 		= new ArrayList<Float>();
		
		outputs_per_move_expected 		= new ArrayList<Float>();
				
		reloadFromFile();
	}
	
	
	private void reloadFromFile() throws Exception {
		
		input = LearningInputFactory.createDefaultInput();
		
		filler = input.createFiller(bitboard);
		
		features_splitter = Features_Splitter.load(Features_Splitter.FEATURES_FILE_NAME, input.getFeaturesConfigurationClassName());
		
		evaluator = (new BagaturEvaluatorFactory_GOLDENMIDDLE()).create(bitboard, null, Bagatur_V20_SignalFiller.eval_config);
		
		
		System.out.println("Trainer_GOLDENMIDDLE.reloadFromFile: weights dump ...");
		
		Features_Splitter.dump(features_splitter);
	}
	
	
	@Override
	public void clear() {
		
		inputs_per_move.clear();
		
		features_per_move_for_update.clear();
		
		outputs_per_move_actual.clear();
		
		outputs_per_move_expected.clear();
	}
	
	
	@Override
	public void addBoardPosition(IBitBoard bitboard) {
		
		
		IFeature[] features = features_splitter.getFeatures(bitboard);
		
		features_per_move_for_update.add(features);
		
		
		ISignals signals = new Signals(features);
		
		filler.fill(signals);
		
		inputs_per_move.add(signals);
		
		
		double actual_eval = evaluator.fullEval(0, IEvaluator.MIN_EVAL, IEvaluator.MAX_EVAL, bitboard.getColourToMove());
		
		if (bitboard.getColourToMove() == Constants.COLOUR_BLACK) {
			
			actual_eval = -actual_eval;
		}
		
		outputs_per_move_actual.add((float) actual_eval);
	}
	
	
	@Override
	public void setGameOutcome(float game_result) {
		
		setGameOutcome_Lmbda(game_result);
	}
	
	
	private void setGameOutcome_Lmbda(float game_result) {
		
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
	
	
	@Override
	public void doEpoch() throws Exception {		
		
		
		if (inputs_per_move.size() != outputs_per_move_actual.size()) {
			
			throw new IllegalStateException();
		}
		
		if (outputs_per_move_actual.size() != outputs_per_move_expected.size()) {
			
			throw new IllegalStateException();
		}
		
		
		for (int moveindex = 0; moveindex < inputs_per_move.size(); moveindex++) {
			
			
			float actualWhitePlayerEval 	= outputs_per_move_actual.get(moveindex);
			
			float expectedWhitePlayerEval 	= outputs_per_move_expected.get(moveindex);
			
			
			double deltaP = expectedWhitePlayerEval - actualWhitePlayerEval;
			
			if (deltaP != 0) {
				
				stats_sum_deltaP += Math.abs(deltaP);
				
				ISignals signals 				= inputs_per_move.get(moveindex);
				
				IFeature[] features 			= features_per_move_for_update.get(moveindex);
				
				for (int i = 0; i < features.length; i++) {
					
					IFeature feature = features[i];
					
					if (feature != null) {
						
						int featureID = feature.getId();
							
						ISignal cur_signal = signals.getSignal(featureID);
						
						if (cur_signal.getStrength() != 0) {
							
							double adjustment = deltaP > 0 ? 1 : -1;
							//double adjustment = deltaP;
							
							((IAdjustableFeature) features[i]).adjust(cur_signal, adjustment, -1);
							
							stats_count_weights_changes++;
							stats_sum_weights_changes += adjustment;
						}
					}
				}
			}
		}
		
		
		update_counter--;
		
		if (update_counter <= 0) {
			
			
			System.out.println("Trainer_GOLDENMIDDLE.doEpoch[updating weights]: stats_sum_deltaP=" + stats_sum_deltaP + ", stats_count_weights_changes=" + stats_count_weights_changes + ", stats_sum_weights_changes=" + stats_sum_weights_changes);
			
			//Features_Splitter.updateWeights(features_splitter, false);
			Features_Splitter.updateWeights(features_splitter, true);
			
			Features_Splitter.store(filename_NN, features_splitter);
			
			
			reloadFromFile();

			
			update_counter = UPDATE_FREQUENCY_GAMES_COUNT;
			
			stats_count_weights_changes = 0;
			stats_sum_weights_changes = 0;
			stats_sum_deltaP = 0;
		}
	}
}
