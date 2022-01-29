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


import java.io.FileInputStream;
import java.io.ObjectInputStream;

import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.deeplearning.ActivationFunction;
import bagaturchess.deeplearning.impl_nnue.visitors.DataSet_1;
import bagaturchess.learning.api.IAdjustableFeature;
import bagaturchess.learning.api.IFeature;
import bagaturchess.learning.api.ISignal;
import bagaturchess.learning.api.ISignals;
import bagaturchess.learning.goldmiddle.impl4.filler.Bagatur_ALL_SignalFiller_InArray;
import bagaturchess.search.api.IEvalConfig;
import deepnetts.net.NeuralNetwork;
import deepnetts.net.train.BackpropagationTrainer;
import deepnetts.util.FileIO;
import deepnetts.util.Tensor;


public class Trainer_IMPL4 extends Trainer_Base {
	
		
	private static final float LEARNING_RATE = 0.0001f;
	
	
	private DataSet_1 dataset;
	
	private Bagatur_ALL_SignalFiller_InArray filler;
	
	private NeuralNetwork network;
	
	private BackpropagationTrainer trainer;
	
	
	public Trainer_IMPL4(IBitBoard _bitboard, String _filename_NN, IEvalConfig _evalConfig) throws Exception {
		
		super(_bitboard, _filename_NN, _evalConfig, ActivationFunction.LINEAR);
		
		
		reloadFromFile();
		
		
		dataset = new DataSet_1();
		
		
		filler = new Bagatur_ALL_SignalFiller_InArray(bitboard);
		
		
		//TODO: Network must be re-created in reloadFromFile, but this doesn't work at the moment.
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename_NN));
		
		network = (NeuralNetwork) ois.readObject();
		
		ois.close();
        
		//TODO: Trainer must be re-created in reloadFromFile, but this doesn't work at the moment.
		trainer = (BackpropagationTrainer) network.getTrainer();
        
        trainer.setMaxEpochs(1)
        //.setOptimizer(OptimizerType.MOMENTUM)
        		.setLearningRate(LEARNING_RATE);
                //.setMaxError(0.000000000000001f);
                //.setBatchMode(true)
                //.setBatchSize(dataset.size());
	}

	
	@Override
	protected void reloadFromFile() throws Exception {
		
		
		super.reloadFromFile();
		
		
		/*filler = new Bagatur_ALL_SignalFiller_InArray(bitboard);
		
		
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename_NN));
		
		network = (NeuralNetwork) ois.readObject();
		
		ois.close();
		
		
		trainer = (BackpropagationTrainer) network.getTrainer();
        
        trainer.setMaxEpochs(1)
        //.setOptimizer(OptimizerType.MOMENTUM)
        		.setLearningRate(LEARNING_RATE);
                //.setMaxError(0.000000000000001f);
                //.setBatchMode(true)
                //.setBatchSize(dataset.size());*/
	}
	
	
	/*@Override
	public void setGameOutcome(float game_result) throws Exception {
		
		if (!dataset.isEmpty()) {
			
			super.setGameOutcome(game_result);
		}
	}*/
	
	
	@Override
	public void addBoardPosition(IBitBoard bitboard) {
		
		
		float[] inputs_1d = new float[55];
		
		filler.fillSignals(inputs_1d, 0);
		
		Tensor tensor = new Tensor(inputs_1d);
		
		inputs_per_move.add(tensor);
		
		
		super.addBoardPosition(bitboard);
	}
	
	
	@Override
	public void backwardView() throws Exception {		
		
		
		for (int moveindex = 0; moveindex < inputs_per_move.size(); moveindex++) {
			
			
			//float actualWhitePlayerEval 	= outputs_per_move_actual.get(moveindex);
			
			float expectedWhitePlayerEval 	= outputs_per_move_expected.get(moveindex);
			
			//double deltaP 					= expectedWhitePlayerEval - actualWhitePlayerEval;
			
			Tensor inputs 					= (Tensor) inputs_per_move.get(moveindex);

			dataset.addItem(inputs, new float[] {expectedWhitePlayerEval});
		}
		
		
		super.backwardView();
	}
	
	
	@Override
	public void updateWeights() throws Exception {
		
		
        trainer.train(dataset);
        
        
        System.out.println("Trainer_IMPL4.updateWeights: Training Accuracy=" + trainer.getTrainingAccuracy());
        
        
		FileIO.writeToFile(network, filename_NN);
		
		
		reloadFromFile();
        
        
		dataset = new DataSet_1();
        
        
		super.updateWeights();
	}
}
