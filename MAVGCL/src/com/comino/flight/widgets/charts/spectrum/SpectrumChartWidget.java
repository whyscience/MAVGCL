/****************************************************************************
 *
 *   Copyright (c) 2016 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.widgets.charts.spectrum;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import org.jtransforms.fft.FloatFFT_1D;
import org.jtransforms.utils.CommonUtils;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.AnalysisDataModelMetaData;
import com.comino.flight.model.KeyFigureMetaData;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.model.service.IAnalysisModelServiceListener;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.flight.widgets.charts.control.IChartControl;
import com.comino.flight.widgets.charts.line.DashBoardAnnotation;
import com.comino.flight.widgets.charts.line.LineMessageAnnotation;
import com.comino.flight.widgets.charts.line.XYDataPool;
import com.comino.flight.widgets.fx.controls.MovingAxis;
import com.comino.flight.widgets.fx.controls.SectionLineChart;
import com.comino.mav.control.IMAVController;
import com.emxsys.chart.extension.XYAnnotations.Layer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.javafx.PlatformUtil;

import javafx.application.Platform;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.LineChart.SortingPolicy;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import pl.edu.icm.jlargearrays.ConcurrencyUtils;
import pl.edu.icm.jlargearrays.LargeArray;


public class SpectrumChartWidget extends BorderPane implements IChartControl, IAnalysisModelServiceListener {

	private static int MAXRECENT = 20;

	private final static int COLLECTOR_CYCLE = 50;
	private final static int REFRESH_RATE    = 50;
	private final static int REFRESH_STEP    = REFRESH_RATE / COLLECTOR_CYCLE;

	@FXML
	private LineChart<Number, Number> barchart;

	@FXML
	private NumberAxis xAxis;

	@FXML
	private NumberAxis yAxis;

	@FXML
	private ChoiceBox<String> group;

	@FXML
	private ChoiceBox<KeyFigureMetaData> cseries1;

	@FXML
	private Button export;


	private int id = 0;

	private  XYChart.Series<Number,Number> series1;

	private IMAVController control;

	private KeyFigureMetaData type1 = null;

	private StateProperties state = null;
	private IntegerProperty timeFrame    = new SimpleIntegerProperty(30);
	private FloatProperty  scroll        = new SimpleFloatProperty(0);

	private int resolution_ms 	= 100;

	private int current_x_pt      = 0;

	private int current_x0_pt     = 0;
	private int current_x1_pt     = 0;

	private AnalysisDataModelMetaData meta = AnalysisDataModelMetaData.getInstance();
	private AnalysisModelService  dataService = AnalysisModelService.getInstance();

	private ArrayList<KeyFigureMetaData> recent = null;

	private Gson gson = new GsonBuilder().create();

	private double x;
	private float timeframe;

	private boolean isPaused = false;


	private XYDataPool pool = null;

	private Preferences prefs = MAVPreferences.getInstance();

	private boolean refreshRequest = false;
	private boolean isRunning = false;

	private final FloatFFT_1D fft;
	private final FloatFFT_1D ifft;


	public SpectrumChartWidget() {

		FXMLLoadHelper.load(this, "SpectrumChartWidget.fxml");

		this.state = StateProperties.getInstance();
		this.pool  = new XYDataPool();

		barchart.setBackground(null);

		series1 = new XYChart.Series<Number,Number>();
		barchart.getData().add(series1);

		dataService.registerListener(this);

		this.fft = new FloatFFT_1D(128);
		this.ifft = new FloatFFT_1D(128);
		LargeArray.setMaxSizeOf32bitArray(1);
		CommonUtils.setThreadsBeginN_1D_FFT_2Threads(1024);
		CommonUtils.setThreadsBeginN_1D_FFT_4Threads(1024);
		ConcurrencyUtils.setNumberOfThreads(10);

	}


	@Override
	public void update(long now) {
		if(!isRunning || isDisabled() || !isVisible() )
			return;
		Platform.runLater(() -> {
			updateGraph(refreshRequest);
		});

	}

	@FXML
	private void initialize() {


		current_x1_pt = timeFrame.intValue() * 1000 / COLLECTOR_CYCLE;

		xAxis.setAutoRanging(false);

		xAxis.setLabel("Freq.");
		xAxis.setAnimated(false);
		xAxis.setLowerBound(1.0);
		xAxis.setUpperBound(64);


		yAxis.setForceZeroInRange(false);
		yAxis.setAutoRanging(false);
		yAxis.setPrefWidth(40);
		yAxis.setAnimated(false);

		yAxis.setLowerBound(0.0);
		yAxis.setUpperBound(100.0);



		barchart.setAnimated(false);
		barchart.setLegendVisible(true);
		barchart.setLegendSide(Side.TOP);
		barchart.setCache(true);
		barchart.setCacheHint(CacheHint.SPEED);

		barchart.prefWidthProperty().bind(widthProperty());
		barchart.prefHeightProperty().bind(heightProperty());

		barchart.setOnMouseClicked(click -> {
			if (click.getClickCount() == 2) {
				if(dataService.isCollecting()) {
					if(isPaused) {
						current_x0_pt =  dataService.calculateX0Index(scroll.get());
						setXResolution(timeFrame.get());
						Platform.runLater(() -> {
							updateGraph(true);
						});
						isRunning = true;
					}
					else {
						isRunning = false;
					}
					isPaused = !isPaused;
				}
				else {
					scroll.set(1);
					current_x0_pt =  dataService.calculateX0Index(scroll.get());
					setXResolution(timeFrame.get());
				}
			}
			click.consume();
		});



		readRecentList();


		type1 = new KeyFigureMetaData();

		group.getSelectionModel().selectedItemProperty().addListener((observable, ov, nv) -> {

			if(nv==null)
				return;

			if(nv.contains("All")) {
				initKeyFigureSelection(cseries1, type1, meta.getKeyFigures());
			} else if(nv.contains("used")) {
				initKeyFigureSelection(cseries1, type1, recent);
			}
			else {
				initKeyFigureSelection(cseries1, type1, meta.getGroupMap().get(nv));
			}
		});

		cseries1.getSelectionModel().selectedItemProperty().addListener((observable, ov, nv) -> {

			if(nv!=null && ov != nv) {
				if(nv.hash!=0) {
					addToRecent(nv);
					series1.setName(nv.desc1+" ["+nv.uom+"]   ");
				}
				else
					series1.setName(nv.desc1+"   ");
				type1 = nv;
				prefs.putInt(MAVPreferences.LINECHART_FIG_1+id,nv.hash);
				updateRequest();
			}
		});

		export.setOnAction((ActionEvent event)-> {
			saveAsPng(System.getProperty("user.home"));
			event.consume();
		});

		timeFrame.addListener((v, ov, nv) -> {
			this.current_x_pt = 0;
			setXResolution(timeFrame.get());
			current_x0_pt =  dataService.calculateX0Index(1);
		});


		scroll.addListener((v, ov, nv) -> {
			current_x0_pt =  dataService.calculateX0Index(nv.floatValue());
			setXResolution(timeFrame.get());
		});


		this.disabledProperty().addListener((v, ov, nv) -> {
			if(ov.booleanValue() && !nv.booleanValue()) {
				current_x0_pt = dataService.calculateX0Index(1);
				current_x0_pt = dataService.calculateX0Index(1);
				setXResolution(timeFrame.get());
			}
		});

	}

	public SpectrumChartWidget setup(IMAVController control, int id) {
		this.id      = id;
		this.control = control;

		setXResolution(20);

		series1.setName(type1.desc1);

		state.getRecordingProperty().addListener((o,ov,nv) -> {
			if(nv.booleanValue()) {
				current_x0_pt = 0;
				setXResolution(timeFrame.get());
				scroll.setValue(0);
				isRunning = true;
			} else
				isRunning = false;
		});

		KeyFigureMetaData k1 = meta.getKeyFigureMap().get(prefs.getInt(MAVPreferences.LINECHART_FIG_1+id,0));
		if(k1!=null) type1 = k1;

		meta.addObserver((o,e) -> {

			group.getItems().clear();

			if(e == null) {

				group.getItems().add("Last used...");
				group.getItems().add("All");
				group.getItems().addAll(meta.getGroups());
				group.getSelectionModel().select(0);

				initKeyFigureSelection(cseries1, type1, recent);

			} else {

				group.getItems().add("All");
				group.getItems().addAll(meta.getGroups());
				group.getSelectionModel().select(0);

				initKeyFigureSelection(cseries1, type1, meta.getKeyFigures());
			}

		});

		return this;
	}

	public IntegerProperty getTimeFrameProperty() {
		return timeFrame;
	}

	@Override
	public FloatProperty getScrollProperty() {
		return scroll;
	}

	@Override
	public void refreshChart() {
		current_x0_pt = dataService.calculateX0Index(1);
		if(!isDisabled())
			updateRequest();
	}

	public void saveAsPng(String path) {
		SnapshotParameters param = new SnapshotParameters();
		param.setFill(Color.BLACK);
		WritableImage image = barchart.snapshot(param, null);
		File file = new File(path+"/chart.png");
		try {
			ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
		} catch (IOException e) {  }
	}

	private void addToRecent(KeyFigureMetaData nv) {

		if(recent.size()>MAXRECENT)
			recent.remove(0);

		for(KeyFigureMetaData s : recent) {
			if(s.hash == nv.hash)
				return;
		}
		recent.add(nv);
		storeRecentList();
	}

	private void setXResolution(float frame) {
		if(frame >= 200)
			resolution_ms = 200;
		else if(frame >= 60)
			resolution_ms = 100;
		else
			resolution_ms = 50;

		timeframe = frame;

		if(!isDisabled()) {
			updateRequest();
			Platform.runLater(() -> {
				xAxis.setLabel("Freq");
			});
		}
	}

	private void updateRequest() {
		if(!isDisabled()) {
			if(dataService.isCollecting())
				refreshRequest = true;
			else {
				Platform.runLater(() -> {
					updateGraph(true);
				});
			}
		}
	}

	float[] data = new float[256];

	private  void updateGraph(boolean refresh) {
		float dt_sec = 0; AnalysisDataModel m =null; float v1 ;



		if(isDisabled()) {
			return;
		}

		if(refresh) {

			if(dataService.getModelList().size()==0 && dataService.isCollecting()) {
				refreshRequest = true; return;
			}

			refreshRequest = false;
			pool.invalidateAll();
			series1.getData().clear();

			for(int i=0;i<128/2;i++)
				series1.getData().add(new XYChart.Data<Number,Number>(i,-1));

			current_x_pt  = current_x0_pt;
			current_x1_pt = current_x0_pt + (int)(timeframe * 1000f / COLLECTOR_CYCLE);

		}

		if(current_x_pt<dataService.getModelList().size() && dataService.getModelList().size()>0 ) {

			int max_x = dataService.getModelList().size();
			if((!state.getRecordingProperty().get() || isPaused) && current_x1_pt < max_x)
				max_x = current_x1_pt;


			while(current_x_pt<max_x ) {


				dt_sec = current_x_pt *  COLLECTOR_CYCLE / 1000f;

				if(current_x_pt > 256) {

					if(type1.hash!=0)  {
						for(int i=0;i<256;i++)
							data[i] = dataService.getModelList().get(current_x_pt-256+i).getValue(type1);
                            fft.realForward(hanningWindow(data));

                            for (int i = 0; i < data.length/2; i++) {
                                data[2*i] = (float)Math.sqrt(Math.pow(data[2*i],2) + Math.pow(data[2*i+1],2));
                                data[2*i+1] = 0f;
                            }

                            ifft.realInverse(data,true);
                            for (int i = 1; i < data.length; i++)
                                data[i] = data[i] *100f / data[0];
                            data[0] = 1.0f;


						for(int i=0;i<128/2;i++)
							series1.getData().get(i).setYValue(data[i]);;


					}
				}


				if(current_x_pt > current_x1_pt) {
					if(!isPaused) {
						current_x0_pt += REFRESH_STEP;
						current_x1_pt += REFRESH_STEP;
					}
				}
				current_x_pt++;
			}

		}
	}





	private void initKeyFigureSelection(ChoiceBox<KeyFigureMetaData> series,KeyFigureMetaData type, List<KeyFigureMetaData> kfl) {

		KeyFigureMetaData none = new KeyFigureMetaData();

		Platform.runLater(() -> {

			series.getItems().clear();
			series.getItems().add(none);

			if(kfl.size()==0) {
				series.getSelectionModel().select(0);
				return;
			}

			if(type!=null && type.hash!=0) {
				if(!kfl.contains(type))
					series.getItems().add(type);
				series.getItems().addAll(kfl);
				series.getSelectionModel().select(type);
			} else {
				series.getItems().addAll(kfl);
				series.getSelectionModel().select(0);
			}

		});
	}

	private void storeRecentList() {
		try {
			String rc = gson.toJson(recent);
			prefs.put(MAVPreferences.RECENT_FIGS, rc);
		} catch(Exception w) { }
	}

	private void readRecentList() {

		String rc = prefs.get(MAVPreferences.RECENT_FIGS, null);
		try {
			if(rc!=null)
				recent = gson.fromJson(rc, new TypeToken<ArrayList<KeyFigureMetaData>>() {}.getType());
		} catch(Exception w) { }
		if(recent==null)
			recent = new ArrayList<KeyFigureMetaData>();

	}

	private float[] hanningWindow(float[] recordedData) {

	    // iterate until the last line of the data buffer
	    for (int n = 1; n < recordedData.length; n++) {
	        // reduce unnecessarily performed frequency part of each and every frequency
	        recordedData[n] *= 0.5 * (1 - Math.cos((2 * Math.PI * n)
	                / (recordedData.length - 1)));
	    }
	    // return modified buffer to the FFT function
	    return recordedData;
	}

	public static Float[] Interpolate(Float[] a, String mode) {

	    // Check that have at least the very first and very last values non-null
	    if (!(a[0] != null && a[a.length-1] != null)) return null;

	    ArrayList<Integer> non_null_idx = new ArrayList<Integer>();
	    ArrayList<Integer> steps = new ArrayList<Integer>();

	    int step_cnt = 0;
	    for (int i=0; i<a.length; i++)
	    {
	        if (a[i] != null)
	        {
	            non_null_idx.add(i);
	            if (step_cnt != 0) {
	                steps.add(step_cnt);
	                System.err.println("aDDed step >> " + step_cnt);
	            }
	            step_cnt = 0;
	        }
	        else
	        {
	            step_cnt++;
	        }
	    }

	    Float f_start = null;
	    Float f_end = null;
	    Float f_step = null;
	    Float f_mu = null;

	    int i = 0;
	    while (i < a.length - 1) // Don't do anything for the very last element (which should never be null)
	    {
	        if (a[i] != null && non_null_idx.size() > 1 && steps.size() > 0)
	        {
	            f_start = a[non_null_idx.get(0)];
	            f_end = a[non_null_idx.get(1)];
	            f_step = new Float(1.0) / new Float(steps.get(0) + 1);
	            f_mu = f_step;
	            non_null_idx.remove(0);
	            steps.remove(0);
	        }
	        else if (a[i] == null)
	        {
	            if (mode.equalsIgnoreCase("cosine"))
	                a[i] = CosineInterpolate(f_start, f_end, f_mu);
	            else
	                a[i] = LinearInterpolate(f_start, f_end, f_mu);
	            f_mu += f_step;
	        }
	        i++;
	    }

	    return a;
	}

	public static Float CosineInterpolate(Float y1,Float y2,Float mu)
	{
	    double mu2;

	    mu2 = (1.0f-Math.cos(mu*Math.PI))/2.0f;
	    Float f_mu2 = new Float(mu2);
	    return(y1*(1.0f-f_mu2)+y2*f_mu2);
	}

	public static Float LinearInterpolate(Float y1,Float y2,Float mu)
	{
	    return(y1*(1-mu)+y2*mu);
	}

}
