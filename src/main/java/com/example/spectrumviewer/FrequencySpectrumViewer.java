package com.example.spectrumviewer;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class FrequencySpectrumViewer extends Application {


    private static final int NUM_DATA_POINTS = 1024 ;
    private  LineChart<Number,Number> lineChart;
    private final int BUFF_SIZE =8192;


    @Override
    public void start(Stage primaryStage)
    {
        BorderPane root = new BorderPane();
        //Creating Line Chart
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("bin");
        yAxis.setLabel("dBm");
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(false);
        lineChart.setLegendVisible(false);

        root.setCenter(lineChart);

        Scene scene = new Scene(root, 1024, 400);
        primaryStage.setTitle("Frequency Spectrum Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

//        var t = new Client("192.168.1.10",7);
//        t.setDaemon(true);
//        t.start();


        var service = Executors.newSingleThreadExecutor();
        service.submit(this::Kintex7DataReader);

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                service.shutdown();
            }
        });
       
    }

    private class Client extends Thread
    {
        private final String server_address;
        private final int port;

        public Client(String server_address,int port) { this.server_address = server_address ; this.port = port;}
        @Override
        public void run()
        {
            System.out.println("Connecting to server...");
            try(Socket socket = new Socket(server_address,port))
            {
                //socket.setSoTimeout(5000);
                System.out.println("Just connected to " + socket.getRemoteSocketAddress());

                DataInputStream reader = new DataInputStream(socket.getInputStream());
                DataOutputStream writer = new DataOutputStream(socket.getOutputStream());

                writer.write(new byte[]{(byte)0x00,(byte) 0x0f,(byte)0x00d,(byte)0x00a}); //b'\x00\x0f\r\n'

                byte[] buffer = new byte[BUFF_SIZE];

                writer.write(new byte[]{(byte) 0x03,(byte)0x00d,(byte)0x00a});  //b'\x03\r\n'

                while (reader.read(buffer) !=-1)
                {
                    var data = unpack(BUFF_SIZE/2,buffer);

                    var len = data.length/2;
                    int[] data_i = new int[len];
                    int[] data_r = new int[len];

                    int j = 0;

                    for (int i = 0; i < len; i += 2) {
                        data_r[j] = data[i];
                        data_i[j] = data[i + 1];
                        j++;
                    }

                    var x_axis = IntStream.range(0, data_r.length).toArray();

                    var y_axis = calculateAmp(data_r, data_i);

                    final XYChart.Series<Number, Number> series = new XYChart.Series<>();
                    for (int i = 3; i < 1024; i++)
                    {
                        XYChart.Data<Number, Number> dataPoint = new XYChart.Data<Number, Number>(x_axis[i], y_axis[i]);
                        series.getData().add(dataPoint);

                    }

                    Platform.runLater(() ->
                    {
                        lineChart.setData(FXCollections.observableArrayList(Collections.singleton(series)));
                    });

                    writer.write(new byte[]{(byte) 0x03,(byte)0x00d,(byte)0x00a});  //b'\x03\r\n'
                }

            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }

    }

    private void Eclypsez7DataReader()
    {
        final int data_length = 1024;

        try
        {
            Path path = Path.of("iq_data3.iq");
            var txt = Files.readString(path);
            var data =  txt.split("\r");

            var start ="AA55";

            for(int index=0; index < data.length ; )
            {

                   if(data[index].equalsIgnoreCase(start)) // Start from "AA55" to "55AA"
                   {
                       var s = index+1;
                       var data_segment = Arrays.copyOfRange(data,s,s+1024);

                       int[][] eclypsez_data = new int[2][data_length/2];

                       index+= 1024;

                       for(int i=0; i< data_segment.length/2 ;i+=2)
                       {

                           if(!data_segment[i].startsWith("-"))
                           {
                               eclypsez_data[0][i] = Short.parseShort(data_segment[i]);
                               eclypsez_data[1][i] = Short.parseShort(data_segment[i+1]);
                           }

                       }

                       var y_axis = calculateAmp(eclypsez_data[0], eclypsez_data[1]);

                       var x_axis = IntStream.range(0, y_axis.length).toArray();


                       final XYChart.Series<Number, Number> series = new XYChart.Series<>();
                       for (int i : x_axis)
                       {
                           XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(x_axis[i], y_axis[i]);
                           series.getData().add(dataPoint);

                       }
                       Platform.runLater(() ->
                       {
                           lineChart.setData(FXCollections.observableArrayList(Collections.singleton(series)));
                       });
                       Thread.sleep(150);

                   }
                   else
                       index+=1;
                }
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }


    }

    private void Kintex7DataReader()
    {
       final int  buff_size =8192+4;
       try(InputStream inputStream = new FileInputStream("Kintex7Data.bin"))
        {

            int dataRead = -1;
            byte[] buffer = new byte[buff_size];
            System.out.println("Starting...");
            while ((dataRead =  inputStream.read(buffer))!= -1)
            {
                //Remove "AA55" 4 bytes from buffer
                var real_data = Arrays.copyOfRange(buffer,4,buffer.length);

                // real_data.length/2 = 4096
                var data = unpack(4096,real_data);

                var len = data.length/2;
                int[] data_i = new int[len];
                int[] data_r = new int[len];

                int j = 0;

                for (int i = 0; i < len; i += 2) {
                    data_r[j] = data[i];
                    data_i[j] = data[i + 1];
                    j++;
                }

                var x_axis = IntStream.range(0, data_r.length).toArray();

                var y_axis = calculateAmp(data_r, data_i);

                final XYChart.Series<Number, Number> series = new XYChart.Series<>();
                for (int i = 3; i < 500; i++)
                {
                    XYChart.Data<Number, Number> dataPoint = new XYChart.Data<Number, Number>(x_axis[i], y_axis[i]);
                    series.getData().add(dataPoint);

                }

                Thread.sleep(150);

                Platform.runLater(() ->
                {
                    lineChart.setData(FXCollections.observableArrayList(Collections.singleton(series)));
                });
            }

        }
        catch (Exception e)
        {

            e.printStackTrace();
        }

    }

    private double[] calculateAmp(int[] a , int[] b)
    {
        var result = new double[a.length];
        for(int i=0; i< a.length ; i++)
        {
            result[i] = Math.sqrt(Math.pow(a[i],2.0)+ Math.pow(b[i],2.0));

        }
        return result;
    }

    private double[] calculateDBView(int[] idata , int[] qdata)
    {
        if(idata.length != qdata.length)
            return null;

        var exp = 0;
        var result = new double[idata.length];

        for(int i : IntStream.range(0,idata.length).toArray())
        {
            exp = idata[i] * idata[i] + qdata[i] * qdata[i];
            if(exp <= 0)
                exp =1;

            result[i] = 4 * Math.log(exp) / Math.log(10);
        }
        return result;
    }


    private short[] unpack(int len, byte[] raw)
    {
        var result = new short[len];

        int pos = 0;
        int Strindex = 0;
        for(int x = 0; x < len; x++)
        {
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.put(raw[pos]);
            bb.put(raw[pos+1]);
            short shortVal = bb.getShort(0);
            result[Strindex] = shortVal;
            pos += 2;
            Strindex += 1;
        }

        return result;
    }


    private ObservableList<XYChart.Series<Number, Number>> generateChartData() {
        final XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Data");
        final Random rng = new Random();
        for (int i=0; i<NUM_DATA_POINTS; i++) {
            XYChart.Data<Number, Number> dataPoint = new XYChart.Data<Number, Number>(i, rng.nextInt(1000));
            series.getData().add(dataPoint);
        }
        return FXCollections.observableArrayList(Collections.singleton(series));
    }




    public static void main(String[] args) {
        launch(args);
    }
}