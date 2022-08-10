/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.WrapLayout;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Motion.MotionOption;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Head;
import org.openpnp.spi.Driver;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.Nozzle.RotationMode;
import org.openpnp.spi.base.AbstractNozzle;
import org.openpnp.spi.base.AbstractActuator;
import org.openpnp.util.BeanUtils;
import org.openpnp.util.Cycles;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;
// import org.python.core.exceptions;
//import org.python.modules.thread.thread;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;


/**
 * Contains controls, DROs and status for the machine. Controls: C right / left, X + / -, Y + / -, Z
 * + / -, stop, pause, slider for jog increment DROs: X, Y, Z, C Radio buttons to select mm or inch.
 * 
 * @author jason
 */

class UI{
    public static int getOption(String title,String message, String[] options, String optDefault){
        int val = JOptionPane.showOptionDialog(null, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, optDefault);
        return val; 
    }
    public static boolean getConfirmation(String title, String message, String[] options, String optDefault){    
        if (options==null){
            options = new String[]{"Yes", "No"};
        }
        if (optDefault==null){
            optDefault = options[0];
        }
            
        int val = getOption(title, message, options, optDefault);
        if (val == 0){
            return true;
        }
        return false;
    }
    public static Object getUserInput(String msg, Object defaultValue){
        return JOptionPane.showInputDialog(msg, defaultValue);
    }
    public static void showMessage(String msg){
        JOptionPane.showMessageDialog(null, msg);
    }
}

class EndStopConversations{

    public static Machine machine = Configuration.get().getMachine();
    public static Driver driver=machine.getDrivers().get(1);
    public static GcodeDriver gcodeDriver = (GcodeDriver)driver;
    
    public static Map<String, Boolean> checkEndStops(String purpose){
        String commandString = "M119";
        try{
            gcodeDriver.checkOkSendCommandForEndStopStatus(commandString);
        }catch(Exception e){
        }
        long start = System.currentTimeMillis();
        System.out.println("Script Status: Awaiting M119 Response for "+purpose);
        while(!gcodeDriver.isOkRecievedForEndStopStatus()){
            try{
                Thread.sleep(1000);
            }catch(InterruptedException e){
            }
            if(System.currentTimeMillis()-start > 10000){
                System.out.println("Script Status: Could Not Fetch M119 Response");
                return null;
            }
        }
        return loggedResponsesForEndStopStatusToDictionary(gcodeDriver.loggedResponsesForEndStopStatus);
    }

    public static Map<String, Boolean> loggedResponsesForEndStopStatusToDictionary(ArrayList<String> loggedlist){
        Map<String, Boolean> dictionary = new HashMap<String, Boolean>();
        for(int i=0;i<loggedlist.size();i++){
            dictionary.put(loggedlist.get(i).split(": ")[0],loggedlist.get(i).split(": ")[1].equals("TRIGGERED"));
        }
        return dictionary;
    }
}

class MultiThreadProcessforConveyorWidthScript extends Thread {
    
    
    public static Machine machine = Configuration.get().getMachine();
    public static Driver driver=machine.getDrivers().get(1);
    public static GcodeDriver gcodeDriver = (GcodeDriver)driver;
    public static JButton machineButton=JogControlsPanel.machineStartStop;

    
  
    @Override
    public void run() {
        
        String conveyorhorizontalAxisLetter = "U";
        //String conveyorhorizontalAxisEndStopString = "c_min";
        
        int pcb_width=0;
        int conveyorWidthOffSet=0;
        

        boolean processAbortFlag =  !UI.getConfirmation("Confirmation","Starting the Automated Conveyor Width Setting Script?\nPlease remove PCBs from conveyor belt if any. \nIf at any point the process is aborted, you can relaunch the sript.",new String[]{"Start","Abort"},null);
        
        System.out.println("Script Status: Checking Machine Status");
        
        if(!processAbortFlag && !machine.isEnabled()){
            if(UI.getConfirmation("Machine Status: Off","Machine is not Enabled. \nClick 'Start' to start the machine. \nClick 'Abort' or Close this dialog box to abort the script.", new String[]{"Start","Abort"}, null)){
                machineButton.doClick();
                long start = System.currentTimeMillis();
                while(!machine.isEnabled()){
                    System.out.println("Script Status: Turning Machine On");
                    try{
                        Thread.sleep(500);
                    }catch(InterruptedException e){

                    }
                    if(System.currentTimeMillis() - start > 10000){
                        System.out.println("Script Status: Could Not Turn Machine On");
                        processAbortFlag = true;
                        break;
                    }
                }
                machineButton=JogControlsPanel.machineStartStop;
            }
            else{
                processAbortFlag = true;
            }
        }


        if(!processAbortFlag){
            while(true){
                Object o = UI.getUserInput("Enter PCB Width in (mm)",0);
                if(o==null){
                    processAbortFlag = true;
                    UI.showMessage("Recieved 'null'. Cancelled..Exiting from Script");
                    break;
                }
                if (o.toString().equals("")){
                    processAbortFlag = true;
                    UI.showMessage("Recieved 'null'. Cancelled..Exiting from Script");
                    break;
                }
                pcb_width = Integer.parseInt(o.toString());
                if (pcb_width==0){
                    UI.showMessage("PCB Width can't be set to 0 mm.");
                }
                if (pcb_width!=0){
                    UI.showMessage("PCB Width: "+pcb_width+"mm");
                    break;
                }
            }
        }


        if(!processAbortFlag){
            String commandString = "G28" + conveyorhorizontalAxisLetter;
            try{
                gcodeDriver.checkOkSendCommand(commandString);
            }catch(Exception e){
            }
            long start = System.currentTimeMillis();
            while(! gcodeDriver.isOkRecieved()){
                System.out.println("Script Status: Homing Conveyor Horizontal Axis");
                try{
                    Thread.sleep(1000);
                }catch(InterruptedException e){
                }
                if(System.currentTimeMillis() - start > 10000){
                    System.out.println("Script Status: Could Not Home "+conveyorhorizontalAxisLetter);
                    processAbortFlag = true;
                    break;
                }
            }
            if(!processAbortFlag){
                System.out.println("Script Status: Homed "+conveyorhorizontalAxisLetter);
            }
        }


        if(!processAbortFlag){
            while(true){
                Object o = UI.getUserInput("Measure and Enter Conveyor Width OffSet in (mm)",0);
                if(o==null){
                    processAbortFlag = true;
                    UI.showMessage("Recieved 'null'. Cancelled..Exiting from Script");
                    break;
                }
                if (o.toString().equals("")){
                    processAbortFlag = true;
                    UI.showMessage("Recieved 'null'. Cancelled..Exiting from Script");
                    break;
                }
                conveyorWidthOffSet = Integer.parseInt(o.toString());
                if (conveyorWidthOffSet!=0){
                    UI.showMessage("Conveyor Width Off Set: "+conveyorWidthOffSet+"mm");
                    break;
                }
            }
        }


        if(!processAbortFlag){
            String commandString = "G1" + conveyorhorizontalAxisLetter + Integer.toString(pcb_width-conveyorWidthOffSet);
            try{
                gcodeDriver.checkOkSendCommand(commandString);
            }catch(Exception e){
            }
            long start = System.currentTimeMillis();
            while(!gcodeDriver.isOkRecieved()){
                System.out.println("Script Status: Setting Conveyor Horizontal Axis");
                try{
                    Thread.sleep(1000);
                }catch(InterruptedException e){
                }
                if(System.currentTimeMillis() - start > 10000){
                    System.out.println("Script Status: Could Not Set "+conveyorhorizontalAxisLetter);
                    processAbortFlag = true;
                    break;
                }
            }
            if(!processAbortFlag){
                System.out.println("Script Status: Set "+conveyorhorizontalAxisLetter+" with PCB Width");
            }
        }


        if(!processAbortFlag){
            UI.showMessage("Please Check PCB fit at the conveyor");
        }

    }
}

class MultiThreadProcessforConveyorScript extends Thread {
    
    public static Machine machine = Configuration.get().getMachine();
    public static Driver driver=machine.getDrivers().get(1);
    public static GcodeDriver gcodeDriver = (GcodeDriver)driver;
    public static JButton machineButton=JogControlsPanel.machineStartStop;

    @Override
    public void run() {
        

        String conveyor1AxisLetter = "X"; 
        String conveyor1AxisEndStopString = "x_min";
 

        boolean processAbortFlag =  !UI.getConfirmation("Confirmation","Starting the Automated Ramp Script? \nA new dialog box will appear. Closing the dialog box will kill the auto ramp thread.",new String[]{"Start","Abort"},null);
        
        System.out.println("Script Status: Checking Machine Status");
        
        if(!processAbortFlag && !machine.isEnabled()){
            if(UI.getConfirmation("Machine Status: Off","Machine is not Enabled. \nClick 'Start' to start the machine. \nClick 'Abort' or Close this dialog box to abort the script.", new String[]{"Start","Abort"}, null)){
                machineButton.doClick();
                long start = System.currentTimeMillis();
                while(!machine.isEnabled()){
                    System.out.println("Script Status: Turning Machine On");
                    try{
                        Thread.sleep(500);
                    }catch(InterruptedException e){

                    }
                    if(System.currentTimeMillis() - start > 10000){
                        System.out.println("Script Status: Could Not Turn Machine On");
                        processAbortFlag = true;
                        break;
                    }
                }
                machineButton=JogControlsPanel.machineStartStop;
            }
            else{
                processAbortFlag = true;
            }
        }
        if(!processAbortFlag){
            JogControlsPanel.threadforJobScript = new MultiThreadProcessforJobScript();
            JogControlsPanel.threadforJobScript.start();
        }

        if(!processAbortFlag){
            MainFrame.nJobsPlusConveyorScriptPanel.showMessageforConveyorScript("Conveyor Automated Process","Automated Conveyor Started..");
            while(true){    
                if(!processAbortFlag){
                    Map<String, Boolean> dictionary = EndStopConversations.checkEndStops("null checking");
                    if(dictionary==null){
                        processAbortFlag = true;
                    }
                    else{
                        if(dictionary.get(conveyor1AxisEndStopString)){
                            MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("Waiting PCB at IR Sensor 1");
                            if(JogControlsPanel.ishomingNow){
                                while(JogControlsPanel.ishomingNow){
                                    try{
                                        Thread.sleep(200);
                                    }catch(InterruptedException e){
                                    }
                                }
                            }
                            System.out.println("Waiting PCB at IR Sensor 1");
                            while(EndStopConversations.checkEndStops("PCB at IR sensor 1").get(conveyor1AxisEndStopString)){
                                try{
                                    Thread.sleep(1500);
                                }catch(InterruptedException e){
                                }
                                if(JogControlsPanel.ishomingNow){
                                    while(JogControlsPanel.ishomingNow){
                                        try{
                                            Thread.sleep(200);
                                        }catch(InterruptedException e){
                                        }
                                    }
                                }
                            }
                        }
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("PCB Ready at IR Sensor 1");
                    }
                }

                if(JogControlsPanel.ishomingNow){
                    while(JogControlsPanel.ishomingNow){
                        try{
                            Thread.sleep(200);
                        }catch(InterruptedException e){
                        }
                    }
                }

                JogControlsPanel.ishomingNow = true;
                
                if(!processAbortFlag){
                    String commandString = "G28" + conveyor1AxisLetter;
                    try{
                        gcodeDriver.checkOkSendCommand(commandString);
                    }catch(Exception e){
                    }
                    long start = System.currentTimeMillis();
                    while(! gcodeDriver.isOkRecieved()){
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("Script Status: Homing "+ conveyor1AxisLetter +"Axis");
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start > 10000){
                            MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("Script Status: Could Not Home "+conveyor1AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    if(!processAbortFlag){
                        JogControlsPanel.setPcbAtFirstIrSensor=true;
                    }
                }

                JogControlsPanel.ishomingNow = false;

                if(!processAbortFlag){
                    if(!JogControlsPanel.setPcbAtSecondIrSensor){
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("Waiting for delivery at 2nd IR Sensor");
                        while(!JogControlsPanel.setPcbAtSecondIrSensor){
                            try{
                                Thread.sleep(1000);
                            }catch(InterruptedException e){
                            }
                        }
                    }
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("Recieved at 2nd IR Sensor");
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforConveyorScript("Looping Again");
                    JogControlsPanel.setPcbAtSecondIrSensor=false;
                }


                if(processAbortFlag){

                    MainFrame.showMessageforScript("Error Occured", "Error Occured at Conveyor Thread. Check Logs Panel.");
                    break;
                }
            }
        }
        if(processAbortFlag){
            if(JogControlsPanel.threadforJobScript!=null){
                JogControlsPanel.threadforJobScript.stop();
            }
            JogControlsPanel.threadforJobScript=null;
            JogControlsPanel.threadforConveyorScript=null;


            NJobsPlusConveyorScriptPanel.panelforConveyorScript.removeAll();
            NJobsPlusConveyorScriptPanel.panelforConveyorScript.revalidate();
            NJobsPlusConveyorScriptPanel.panelforConveyorScript.repaint();
            NJobsPlusConveyorScriptPanel.scrollPaneforConveyorScript.revalidate();
            NJobsPlusConveyorScriptPanel.scrollPaneforConveyorScript.repaint();
            NJobsPlusConveyorScriptPanel.lablelforConveyorScript=null;
            NJobsPlusConveyorScriptPanel.strforConveyorScript=null;

            NJobsPlusConveyorScriptPanel.panelforJobScript.removeAll();
            NJobsPlusConveyorScriptPanel.panelforJobScript.revalidate();
            NJobsPlusConveyorScriptPanel.panelforJobScript.repaint();
            NJobsPlusConveyorScriptPanel.scrollPaneforJobScript.revalidate();
            NJobsPlusConveyorScriptPanel.scrollPaneforJobScript.repaint();
            NJobsPlusConveyorScriptPanel.lablelforJobScript=null;
            NJobsPlusConveyorScriptPanel.strforJobScript=null;

            MainFrame.nJobsPlusConveyorScriptPanel.remove(MainFrame.nJobsPlusConveyorScriptPanel.abortButton);
            MainFrame.nJobsPlusConveyorScriptPanel.revalidate();
            MainFrame.nJobsPlusConveyorScriptPanel.repaint();


            this.stop();
        }
    }
}

class MultiThreadProcessforJobScript extends Thread {
    
    
    public static Machine machine = Configuration.get().getMachine();
    public static Driver driver=machine.getDrivers().get(1);
    public static GcodeDriver gcodeDriver = (GcodeDriver)driver;
    public static JButton machineButton=JogControlsPanel.machineStartStop;
    Actuator actuator = machine.getActuatorByName("Clamping");
    Actuator midActuator = machine.getActuatorByName("0Clamping");


      
    @Override
    public void run() {
        List<BoardLocation> boardLocations = MainFrame.get().getMachineControls().getJobPanel().getJob().getBoardLocations();
        if(boardLocations.size()==0){
            MainFrame.showMessageforScript("No Board Imported", "Empty Board List Found. Please Import a Board before staring the Script.");
            NJobsPlusConveyorScriptPanel.abortButton.doClick();
        }

        BoardLocation boardLocation = boardLocations.get(0);
        List<Placement> allPlacements = boardLocation.getBoard().getPlacements();
        List<String> enabledButNotPlacedPlacementsId = new ArrayList<>();

        JobPanel.jobPlayPauseButton.setEnabled(false);
        
        

        String conveyor1AxisLetter = "X";
        String conveyor2AxisLetter = "Y";
        String conveyor3AxisLetter = "Z";

        // String conveyor1AxisEndStopString = "x_max";
        String conveyor2AxisEndStopString = "y_min";
        //String conveyor3AxisEndStopString = "z_min";
        
        int numberOfPcbs=0;
        
        // boolean processAbortFlag =  !UI.getConfirmation("Confirmation","Starting the Job Script? \nMake Sure Auto Ramp Script is already running. A new dialog box will appear. Closing the dialog box at any point will kill the Job thread.",new String[]{"Start","Abort"},null);
        
        boolean processAbortFlag = false;

        if(!processAbortFlag){
            if(JogControlsPanel.threadforConveyorScript==null){
                UI.showMessage("Please Start the Auto Ramp First");
                processAbortFlag = true;
            }
        }

        if(!processAbortFlag){
            while(true){
                Object o = UI.getUserInput("Enter Number Of PCBs",0);
                if(o==null){
                    processAbortFlag = true;
                    UI.showMessage("Recieved 'null'. Cancelled..Exiting from Script");
                    break;
                }
                if (o.toString().equals("")){
                    processAbortFlag = true;
                    UI.showMessage("Recieved 'null'. Cancelled..Exiting from Script");
                    break;
                }
                numberOfPcbs = Integer.parseInt(o.toString());
                if (numberOfPcbs==0){
                    UI.showMessage("Number Of PCBs can't be set to 0 mm.");
                }
                if (numberOfPcbs!=0){
                    UI.showMessage("Number Of PCBs "+numberOfPcbs);
                    break;
                }
            }
        }
        
        int n = numberOfPcbs;

        if(!processAbortFlag){
            MainFrame.nJobsPlusConveyorScriptPanel.showMessageforJobScript("n Jobs for n Number Of PCBs","n Jobs Script Started..");
            while(n-->0){
                
                if(!processAbortFlag){
                    if(!JogControlsPanel.setPcbAtFirstIrSensor){
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Waiting set PCB at IR Sensor 1");
                        System.out.println("Waiting set PCB at IR Sensor 1");
                        while(!JogControlsPanel.setPcbAtFirstIrSensor){
                            try{
                                Thread.sleep(1000);
                            }catch(InterruptedException e){
                            }
                        }
                    }
                    JogControlsPanel.setPcbAtFirstIrSensor = false;
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("PCB set at IR Sensor 1");

                    if(JogControlsPanel.ishomingNow){
                        while(JogControlsPanel.ishomingNow){
                            try{
                                Thread.sleep(200);
                            }catch(InterruptedException e){
                            }
                        }
                    }

                    if(!EndStopConversations.checkEndStops("Empty 2nd Sensor").get(conveyor2AxisEndStopString)){
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Empty IR Sensor 2.. Caliing");
                        UiUtils.submitUiMachineTask(() -> {
                            AbstractActuator.assertOnOffDefined(midActuator);
                            midActuator.actuate(true);
                        });
                    }
                    else{
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Error: Something Detected at IR Sensor 2");
                        processAbortFlag = true;
                        break;
                    }
                }

                if(!processAbortFlag){
                    String commandString_ = "G92" + conveyor2AxisLetter + Integer.toString(0);
                    try{
                        gcodeDriver.checkOkSendCommand(commandString_);
                    }catch(Exception e){
                    }
                    long start_ = System.currentTimeMillis();
                    while(!gcodeDriver.isOkRecieved()){
                        System.out.println("Script Status: Setting Conveyor axis 2 to 0");
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start_ > 10000){
                            System.out.println("Script Status: Could Not Set "+conveyor2AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    String commandString = "G1" + conveyor1AxisLetter + Integer.toString(-250) + conveyor2AxisLetter + Integer.toString(-250);
                    try{
                        gcodeDriver.checkOkSendCommand(commandString);
                    }catch(Exception e){
                    }
                    long start = System.currentTimeMillis();
                    while(!gcodeDriver.isOkRecieved()){
                        System.out.println("Script Status: Moving Conveyor Axis 1 and 2");
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start > 10000){
                            System.out.println("Script Status: Could Not Move "+conveyor1AxisLetter+conveyor2AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    if(!processAbortFlag){
                        System.out.println("Script Status: Moved "+conveyor1AxisLetter+conveyor2AxisLetter+ " by 250");
                    }
                }

                try{
                    Thread.sleep(3000);
                }catch(InterruptedException e){

                }

                if(JogControlsPanel.ishomingNow){
                    while(JogControlsPanel.ishomingNow){
                        try{
                            Thread.sleep(200);
                        }catch(InterruptedException e){
                        }
                    }
                }

                JogControlsPanel.ishomingNow = true;

                if(!processAbortFlag){
                    String commandString = "G28" + conveyor2AxisLetter;
                    try{
                        gcodeDriver.checkOkSendCommand(commandString);
                    }catch(Exception e){
                    }
                    long start = System.currentTimeMillis();
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Script Status: Homing "+ conveyor2AxisLetter +"Axis");
                    while(! gcodeDriver.isOkRecieved()){
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start > 20000){
                            MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Script Status: Could Not Home "+conveyor2AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    if(!processAbortFlag){
                        JogControlsPanel.setPcbAtSecondIrSensor=true;
                    }
                }

                JogControlsPanel.ishomingNow = false;


                if(!processAbortFlag){

                    String commandString = "G1" + conveyor2AxisLetter + Integer.toString(-100);
                    try{
                        gcodeDriver.checkOkSendCommand(commandString);
                    }catch(Exception e){
                    }
                    long start = System.currentTimeMillis();
                    while(!gcodeDriver.isOkRecieved()){
                        System.out.println("Script Status: Moving Conveyor Axis 2 to set at middle according to PCB height...");
                        //  Height Can Be Taken From The Use In The Script Along With No. Of PCBs
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start > 10000){
                            System.out.println("Script Status: Could Not Move "+conveyor2AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    if(!processAbortFlag){
                        System.out.println("Script Status: Moved "+conveyor2AxisLetter);
                    }
                }

                if(!processAbortFlag){
                    try{
                        Thread.sleep(5000);
                    }catch(InterruptedException e){
                    }
                }

                if(!processAbortFlag){

                    

                    UiUtils.submitUiMachineTask(() -> {
                        AbstractActuator.assertOnOffDefined(actuator);
                        actuator.actuate(true);
                    });
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Clamping..");
                    try{
                        Thread.sleep(2000);
                    }catch(InterruptedException e){
                    }

                    
                    if(n==numberOfPcbs-1){
                        MainFrame.showMessageforScript("Important!", "Press 'Continue' once you have set the machine ready to perform the Job.");
                    }


                    JobPanel.jobPlayPauseButton.setEnabled(true);

                    enabledButNotPlacedPlacementsId.clear();
                    for(Placement p: allPlacements){
                        if(p.isEnabled() && !boardLocation.getPlaced(p.getId())){
                            enabledButNotPlacedPlacementsId.add(p.getId());
                            System.out.println(p.getId());
                        }
                    }
                    JobPanel.jobPlayPauseButton.doClick();
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Doing Job");
                    
                    while(!JogControlsPanel.isJobComplete){
                        try{
                            Thread.sleep(2000);
                        }catch(InterruptedException e){
                        }
                    }
                    
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Job Done");
                    
                    JobPanel.jobPlayPauseButton.setEnabled(false);

                    for(String id: enabledButNotPlacedPlacementsId){
                        boardLocation.setPlaced(id, false);;
                    }
                    MainFrame.get().getMachineControls().getJobPanel().getJobPlacementsPanel().refresh();

                    JogControlsPanel.isJobComplete = false;
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Undoing Job Done Status");
                    
                    
                    try{
                        Thread.sleep(1000);
                    }catch(InterruptedException e){
                    }

                    UiUtils.submitUiMachineTask(() -> {
                        AbstractActuator.assertOnOffDefined(actuator);
                        actuator.actuate(false);
                    });
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Un-Clamping..");
                
                
                    try{
                        Thread.sleep(4000);
                    }catch(InterruptedException e){
                    }

                    
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Un-Clamped");
                }

                if(JogControlsPanel.ishomingNow){
                    while(JogControlsPanel.ishomingNow){
                        try{
                            Thread.sleep(200);
                        }catch(InterruptedException e){
                        }
                    }
                }


                if(!processAbortFlag){
                    String commandString_ = "G92" + conveyor3AxisLetter + Integer.toString(0);
                    try{
                        gcodeDriver.checkOkSendCommand(commandString_);
                    }catch(Exception e){
                    }
                    long start_ = System.currentTimeMillis();
                    while(!gcodeDriver.isOkRecieved()){
                        System.out.println("Script Status: Setting "+conveyor3AxisLetter+" to 00");
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start_ > 10000){
                            System.out.println("Script Status: Could Not Set "+conveyor3AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    String commandString = "G1" + conveyor2AxisLetter + Integer.toString(-400) + conveyor3AxisLetter + Integer.toString(-400);
                    try{
                        gcodeDriver.checkOkSendCommand(commandString);
                    }catch(Exception e){
                    }
                    long start = System.currentTimeMillis();
                    while(!gcodeDriver.isOkRecieved()){
                        System.out.println("Script Status: Moving Conveyor Axis 2");
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start > 10000){
                            System.out.println("Script Status: Could Not Move "+conveyor2AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    if(!processAbortFlag){
                        System.out.println("Script Status: Moved "+conveyor2AxisLetter+" "+conveyor3AxisLetter +" by 400");
                    }
                }

                try{
                    Thread.sleep(3000);
                }catch(InterruptedException e){
                    
                }

                if(JogControlsPanel.ishomingNow){
                    while(JogControlsPanel.ishomingNow){
                        try{
                            Thread.sleep(200);
                        }catch(InterruptedException e){
                        }
                    }
                }

                JogControlsPanel.ishomingNow = true;

                if(!processAbortFlag){
                    String commandString = "G28" + conveyor3AxisLetter;
                    try{
                        gcodeDriver.checkOkSendCommand(commandString);
                    }catch(Exception e){
                    }
                    long start = System.currentTimeMillis();
                    MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Script Status: Homing "+ conveyor3AxisLetter +"Axis");
                    while(!gcodeDriver.isOkRecieved()){
                        try{
                            Thread.sleep(1000);
                        }catch(InterruptedException e){
                        }
                        if(System.currentTimeMillis() - start > 30000){
                            MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Script Status: Could Not Home "+conveyor3AxisLetter);
                            processAbortFlag = true;
                            break;
                        }
                    }
                    if(!processAbortFlag){
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Homed "+conveyor3AxisLetter);

                        //Actuating 0Clamping to false.
                        UiUtils.submitUiMachineTask(() -> {
                            AbstractActuator.assertOnOffDefined(midActuator);
                            midActuator.actuate(true);
                        });
                        MainFrame.nJobsPlusConveyorScriptPanel.appendMessageforJobScript("Looping Again");
                    }
                    if(processAbortFlag){

                        MainFrame.showMessageforScript("Error Occured", "Error Occured at Job Thread. Check Logs Panel.");
                        break;
                    }
                }

                JogControlsPanel.ishomingNow = false;
        
                if(processAbortFlag){
                    MainFrame.showMessageforScript("Error Occured", "Error Occured at Job Thread. Check Logs Panel.");
                    break;
                }
            }

            if(processAbortFlag){
                MainFrame.showMessageforScript("Error Occured", "Error Occured at Job Thread. Check Logs Panel.");
            }

            if(!processAbortFlag){
                MainFrame.showMessageforScript("Completed","Process Completed with " + numberOfPcbs +" PCB(s)." );
            }
        }
        if(processAbortFlag || n==-1){
            // NJobsPlusConveyorScriptPanel.abortButton.doClick();
            JobPanel.jobPlayPauseButton.setEnabled(true);

            if(JogControlsPanel.threadforConveyorScript!=null){
                JogControlsPanel.threadforConveyorScript.stop();
            }
            JogControlsPanel.threadforConveyorScript=null;
            JogControlsPanel.threadforJobScript=null;

            NJobsPlusConveyorScriptPanel.panelforConveyorScript.removeAll();
            NJobsPlusConveyorScriptPanel.panelforConveyorScript.revalidate();
            NJobsPlusConveyorScriptPanel.panelforConveyorScript.repaint();
            NJobsPlusConveyorScriptPanel.scrollPaneforConveyorScript.revalidate();
            NJobsPlusConveyorScriptPanel.scrollPaneforConveyorScript.repaint();
            NJobsPlusConveyorScriptPanel.lablelforConveyorScript=null;
            NJobsPlusConveyorScriptPanel.strforConveyorScript=null;

            NJobsPlusConveyorScriptPanel.panelforJobScript.removeAll();
            NJobsPlusConveyorScriptPanel.panelforJobScript.revalidate();
            NJobsPlusConveyorScriptPanel.panelforJobScript.repaint();
            NJobsPlusConveyorScriptPanel.scrollPaneforJobScript.revalidate();
            NJobsPlusConveyorScriptPanel.scrollPaneforJobScript.repaint();
            NJobsPlusConveyorScriptPanel.lablelforJobScript=null;
            NJobsPlusConveyorScriptPanel.strforJobScript=null;

            MainFrame.nJobsPlusConveyorScriptPanel.remove(MainFrame.nJobsPlusConveyorScriptPanel.abortButton);
            MainFrame.nJobsPlusConveyorScriptPanel.revalidate();
            MainFrame.nJobsPlusConveyorScriptPanel.repaint();

            this.stop();
        }
    }
}

public class JogControlsPanel extends JPanel {
    
    public static MultiThreadProcessforJobScript threadforJobScript;
    public static MultiThreadProcessforConveyorScript threadforConveyorScript;
    public MultiThreadProcessforConveyorWidthScript threadforConveyorWidthScript;
    
    public static boolean ishomingNow = false;

    private final MachineControlsPanel machineControlsPanel;
    private final Configuration configuration;
    private JPanel panelActuators;
    private JSlider sliderIncrements;
    private JCheckBox boardProtectionOverrideCheck;
    public static JButton machineStartStop;

    public static boolean setPcbAtFirstIrSensor=false;
    public static boolean setPcbAtSecondIrSensor=false;
    public static boolean isJobComplete=false;
    /**
     * Create the panel.
     */
    public JogControlsPanel(Configuration configuration,
            MachineControlsPanel machineControlsPanel) {
        this.machineControlsPanel = machineControlsPanel;
        this.configuration = configuration;

        createUi();

        configuration.addListener(configurationListener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        xPlusAction.setEnabled(enabled);
        xMinusAction.setEnabled(enabled);
        yPlusAction.setEnabled(enabled);
        yMinusAction.setEnabled(enabled);
        zPlusAction.setEnabled(enabled);
        zMinusAction.setEnabled(enabled);
        cPlusAction.setEnabled(enabled);
        cMinusAction.setEnabled(enabled);
        discardAction.setEnabled(enabled);
        safezAction.setEnabled(enabled);
        xyParkAction.setEnabled(enabled);
        zParkAction.setEnabled(enabled);
        cParkAction.setEnabled(enabled);
        for (Component c : panelActuators.getComponents()) {
            c.setEnabled(enabled);
        }
    }

    private void setUnits(LengthUnit units) {
        if (units == LengthUnit.Millimeters) {
            Hashtable<Integer, JLabel> incrementsLabels = new Hashtable<>();
            incrementsLabels.put(1, new JLabel("0.01")); //$NON-NLS-1$
            incrementsLabels.put(2, new JLabel("0.1")); //$NON-NLS-1$
            incrementsLabels.put(3, new JLabel("1.0")); //$NON-NLS-1$
            incrementsLabels.put(4, new JLabel("10")); //$NON-NLS-1$
            incrementsLabels.put(5, new JLabel("100")); //$NON-NLS-1$
            sliderIncrements.setLabelTable(incrementsLabels);
        }
        else if (units == LengthUnit.Inches) {
            Hashtable<Integer, JLabel> incrementsLabels = new Hashtable<>();
            incrementsLabels.put(1, new JLabel("0.001")); //$NON-NLS-1$
            incrementsLabels.put(2, new JLabel("0.01")); //$NON-NLS-1$
            incrementsLabels.put(3, new JLabel("0.1")); //$NON-NLS-1$
            incrementsLabels.put(4, new JLabel("1.0")); //$NON-NLS-1$
            incrementsLabels.put(5, new JLabel("10.0")); //$NON-NLS-1$
            sliderIncrements.setLabelTable(incrementsLabels);
        }
        else {
            throw new Error("setUnits() not implemented for " + units); //$NON-NLS-1$
        }
        machineControlsPanel.updateDros();
    }

    public double getJogIncrement() {
        if (configuration.getSystemUnits() == LengthUnit.Millimeters) {
            return 0.01 * Math.pow(10, sliderIncrements.getValue() - 1);
        }
        else if (configuration.getSystemUnits() == LengthUnit.Inches) {
            return 0.001 * Math.pow(10, sliderIncrements.getValue() - 1);
        }
        else {
            throw new Error(
                    "getJogIncrement() not implemented for " + configuration.getSystemUnits()); //$NON-NLS-1$
        }
    }

    public boolean getBoardProtectionOverrideEnabled() {
        return boardProtectionOverrideCheck.isSelected();
    }

    private void jog(final int x, final int y, final int z, final int c) {
        UiUtils.submitUiMachineTask(() -> {
            HeadMountable tool = machineControlsPanel.getSelectedTool();
            jogTool(x, y, z, c, tool);
        });
    }

    public void jogTool(final int x, final int y, final int z, final int c, HeadMountable tool)
            throws Exception {
        Location l = tool.getLocation()
                .convertToUnits(Configuration.get()
                        .getSystemUnits());
        double xPos = l.getX();
        double yPos = l.getY();
        double zPos = l.getZ();
        double cPos = l.getRotation();

        double jogIncrement =
                new Length(getJogIncrement(), configuration.getSystemUnits()).getValue();

        if (x > 0) {
            xPos += jogIncrement;
        }
        else if (x < 0) {
            xPos -= jogIncrement;
        }

        if (y > 0) {
            yPos += jogIncrement;
        }
        else if (y < 0) {
            yPos -= jogIncrement;
        }

        if (z > 0) {
            zPos += jogIncrement;
        }
        else if (z < 0) {
            zPos -= jogIncrement;
        }

        if (c > 0) {
            cPos += jogIncrement;
        }
        else if (c < 0) {
            cPos -= jogIncrement;
        }

        Location targetLocation = new Location(l.getUnits(), xPos, yPos, zPos, cPos);
        if (!this.getBoardProtectionOverrideEnabled()) {
            /* check board location before movement */
            List<BoardLocation> boardLocations = machineControlsPanel.getJobPanel()
                    .getJob()
                    .getBoardLocations();
            for (BoardLocation boardLocation : boardLocations) {
                if (!boardLocation.isEnabled()) {
                    continue;
                }
                boolean safe = nozzleLocationIsSafe(boardLocation.getLocation(),
                        boardLocation.getBoard()
                        .getDimensions(),
                        targetLocation, new Length(1.0, l.getUnits()));
                if (!safe) {
                    throw new Exception(
                            "Nozzle would crash into board: " + boardLocation.toString() + "\n" + //$NON-NLS-1$ //$NON-NLS-2$
                            "To disable the board protection go to the \"Safety\" tab in the \"Machine Controls\" panel."); //$NON-NLS-1$
                }
            }
        }

        tool.moveTo(targetLocation, MotionOption.JogMotion); 

        MovableUtils.fireTargetedUserAction(tool, true);
    }

    private boolean nozzleLocationIsSafe(Location origin, Location dimension, Location nozzle,
            Length safeDistance) {
        double distance = safeDistance.convertToUnits(nozzle.getUnits())
                .getValue();
        Location originConverted = origin.convertToUnits(nozzle.getUnits());
        Location dimensionConverted = dimension.convertToUnits(dimension.getUnits());
        double boardUpperZ = originConverted.getZ();
        boolean containsXY = pointWithinRectangle(originConverted, dimensionConverted, nozzle);
        boolean containsZ = nozzle.getZ() <= (boardUpperZ + distance);
        return !(containsXY && containsZ);
    }

    private boolean pointWithinRectangle(Location origin, Location dimension, Location point) {
        double rotation = Math.toRadians(origin.getRotation());
        double ay = origin.getY() + Math.sin(rotation) * dimension.getX();
        double ax = origin.getX() + Math.cos(rotation) * dimension.getX();
        Location a = new Location(dimension.getUnits(), ax, ay, 0.0, 0.0);

        double cx = origin.getX() - Math.cos(Math.PI / 2 - rotation) * dimension.getY();
        double cy = origin.getY() + Math.sin(Math.PI / 2 - rotation) * dimension.getY();
        Location c = new Location(dimension.getUnits(), cx, cy, 0.0, 0.0);

        double bx = ax + cx - origin.getX();
        double by = ay + cy - origin.getY();
        Location b = new Location(dimension.getUnits(), bx, by, 0.0, 0.0);

        return pointWithinTriangle(origin, b, a, point) || pointWithinTriangle(origin, c, b, point);
    }

    private boolean pointWithinTriangle(Location p1, Location p2, Location p3, Location p) {
        double alpha = ((p2.getY() - p3.getY()) * (p.getX() - p3.getX())
                + (p3.getX() - p2.getX()) * (p.getY() - p3.getY()))
                / ((p2.getY() - p3.getY()) * (p1.getX() - p3.getX())
                        + (p3.getX() - p2.getX()) * (p1.getY() - p3.getY()));
        double beta = ((p3.getY() - p1.getY()) * (p.getX() - p3.getX())
                + (p1.getX() - p3.getX()) * (p.getY() - p3.getY()))
                / ((p2.getY() - p3.getY()) * (p1.getX() - p3.getX())
                        + (p3.getX() - p2.getX()) * (p1.getY() - p3.getY()));
        double gamma = 1.0 - alpha - beta;

        return (alpha > 0.0) && (beta > 0.0) && (gamma > 0.0);
    }

    private void createUi() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        setFocusTraversalPolicy(focusPolicy);
        setFocusTraversalPolicyProvider(true);

        JTabbedPane tabbedPane_1 = new JTabbedPane(JTabbedPane.TOP);
        add(tabbedPane_1);

        JPanel panelControls = new JPanel();
        tabbedPane_1.addTab("Jog", null, panelControls, null); //$NON-NLS-1$
        panelControls.setLayout(new FormLayout(
                new ColumnSpec[] {FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,},
                new RowSpec[] {FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

        JButton homeButton = new JButton(machineControlsPanel.homeAction);
        // We set this Icon explicitly as a WindowBuilder helper. WindowBuilder can't find the
        // homeAction referenced above so the icon doesn't render in the viewer. We set it here
        // so the dialog looks right while editing.
        homeButton.setIcon(Icons.home);
        homeButton.setHideActionText(true);
        homeButton.setToolTipText(Translations.getString("JogControlsPanel.homeButton.toolTipText")); //$NON-NLS-1$ //$NON-NLS-1$
        panelControls.add(homeButton, "2, 2"); //$NON-NLS-1$

        JLabel lblXy = new JLabel("X/Y"); //$NON-NLS-1$
        lblXy.setFont(new Font("Lucida Grande", Font.PLAIN, 22)); //$NON-NLS-1$
        lblXy.setHorizontalAlignment(SwingConstants.CENTER);
        panelControls.add(lblXy, "8, 2, fill, default"); //$NON-NLS-1$

        JLabel lblZ = new JLabel("Z"); //$NON-NLS-1$
        lblZ.setHorizontalAlignment(SwingConstants.CENTER);
        lblZ.setFont(new Font("Lucida Grande", Font.PLAIN, 22)); //$NON-NLS-1$
        panelControls.add(lblZ, "14, 2"); //$NON-NLS-1$

        JLabel lblDistance = new JLabel("<html>" + Translations.getString("JogControlsPanel.Label.Distance") + "<br>[" + configuration.getSystemUnits().getShortName() + "/deg]</html>"); //$NON-NLS-1$
        lblDistance.setFont(new Font("Lucida Grande", Font.PLAIN, 10)); //$NON-NLS-1$
        panelControls.add(lblDistance, "18, 2, center, center"); //$NON-NLS-1$

        JLabel lblSpeed = new JLabel("<html>" + Translations.getString("JogControlsPanel.Label.Speed") + "<br>[%]</html>"); //$NON-NLS-1$
        lblSpeed.setFont(new Font("Lucida Grande", Font.PLAIN, 10)); //$NON-NLS-1$
        panelControls.add(lblSpeed, "20, 2, center, center"); //$NON-NLS-1$

        sliderIncrements = new JSlider();
        panelControls.add(sliderIncrements, "18, 3, 1, 10"); //$NON-NLS-1$
        sliderIncrements.setOrientation(SwingConstants.VERTICAL);
        sliderIncrements.setMajorTickSpacing(1);
        sliderIncrements.setValue(1);
        sliderIncrements.setSnapToTicks(true);
        sliderIncrements.setPaintLabels(true);
        sliderIncrements.setMinimum(1);
        sliderIncrements.setMaximum(5);

        JButton yPlusButton = new JButton(yPlusAction);
        yPlusButton.setHideActionText(true);
        panelControls.add(yPlusButton, "8, 4"); //$NON-NLS-1$

        JButton zUpButton = new JButton(zPlusAction);
        zUpButton.setHideActionText(true);
        panelControls.add(zUpButton, "14, 4"); //$NON-NLS-1$

        speedSlider = new JSlider();
        speedSlider.setValue(100);
        speedSlider.setPaintTicks(true);
        speedSlider.setMinorTickSpacing(1);
        speedSlider.setMajorTickSpacing(25);
        speedSlider.setSnapToTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.setOrientation(SwingConstants.VERTICAL);
        panelControls.add(speedSlider, "20, 4, 1, 9"); //$NON-NLS-1$
        speedSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Configuration.get()
                .getMachine()
                .setSpeed(speedSlider.getValue() * 0.01);
            }
        });

        JButton positionNozzleBtn = new JButton(machineControlsPanel.targetToolAction);
        positionNozzleBtn.setIcon(Icons.centerTool);
        positionNozzleBtn.setHideActionText(true);
        positionNozzleBtn.setToolTipText(Translations.getString("JogControlsPanel.Action.positionSelectedNozzle"));
        panelControls.add(positionNozzleBtn, "22, 4"); //$NON-NLS-1$

        JButton widthScriptButton = new JButton(startWidthScript);
        panelControls.add(widthScriptButton, "2, 8"); //$NON-NLS-1$

        //JButton actuateClampButton = new JButton(actuateClamping);
        //panelControls.add(actuateClampButton, "2, 10"); //$NON-NLS-1$

        JButton buttonStartStop = new JButton(machineControlsPanel.startStopMachineAction);
        machineStartStop = buttonStartStop;
        buttonStartStop.setIcon(Icons.powerOn);
        panelControls.add(buttonStartStop, "2, 6"); //$NON-NLS-1$
        buttonStartStop.setHideActionText(true);

        JButton xMinusButton = new JButton(xMinusAction);
        xMinusButton.setHideActionText(true);
        panelControls.add(xMinusButton, "6, 6"); //$NON-NLS-1$

        JButton homeXyButton = new JButton(xyParkAction);
        homeXyButton.setHideActionText(true);
        panelControls.add(homeXyButton, "8, 6"); //$NON-NLS-1$

        JButton xPlusButton = new JButton(xPlusAction);
        xPlusButton.setHideActionText(true);
        panelControls.add(xPlusButton, "10, 6"); //$NON-NLS-1$

        JButton homeZButton = new JButton(zParkAction);
        homeZButton.setHideActionText(true);
        panelControls.add(homeZButton, "14, 6"); //$NON-NLS-1$

        JButton yMinusButton = new JButton(yMinusAction);
        yMinusButton.setHideActionText(true);
        panelControls.add(yMinusButton, "8, 8"); //$NON-NLS-1$

        JButton zDownButton = new JButton(zMinusAction);
        zDownButton.setHideActionText(true);
        panelControls.add(zDownButton, "14, 8"); //$NON-NLS-1$

        JButton positionCameraBtn = new JButton(machineControlsPanel.targetCameraAction);
        positionCameraBtn.setIcon(Icons.centerCamera);
        positionCameraBtn.setHideActionText(true);
        positionCameraBtn.setToolTipText(Translations.getString("JogControlsPanel.Action.positionCamera"));
        panelControls.add(positionCameraBtn, "22, 8"); //$NON-NLS-1$

        JLabel lblC = new JLabel("C"); //$NON-NLS-1$
        lblC.setHorizontalAlignment(SwingConstants.CENTER);
        lblC.setFont(new Font("Lucida Grande", Font.PLAIN, 22)); //$NON-NLS-1$
        panelControls.add(lblC, "4, 12"); //$NON-NLS-1$

        JButton counterclockwiseButton = new JButton(cPlusAction);
        counterclockwiseButton.setHideActionText(true);
        panelControls.add(counterclockwiseButton, "6, 12"); //$NON-NLS-1$

        JButton homeCButton = new JButton(cParkAction);
        homeCButton.setHideActionText(true);
        panelControls.add(homeCButton, "8, 12"); //$NON-NLS-1$

        JButton clockwiseButton = new JButton(cMinusAction);
        clockwiseButton.setHideActionText(true);
        panelControls.add(clockwiseButton, "10, 12"); //$NON-NLS-1$

        JPanel panelSpecial = new JPanel();
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Special"), null, panelSpecial, null); //$NON-NLS-1$
        FlowLayout flowLayout_1 = (FlowLayout) panelSpecial.getLayout();
        flowLayout_1.setAlignment(FlowLayout.LEFT);

        JButton btnSafeZ = new JButton(safezAction);
        panelSpecial.add(btnSafeZ);

        JButton btnDiscard = new JButton(discardAction);
        panelSpecial.add(btnDiscard);

        JButton btnRecycle = new JButton(recycleAction);
        recycleAction.setEnabled(false);
        btnRecycle.setToolTipText(Translations.getString("JogControlsPanel.btnRecycle.toolTipText")); //$NON-NLS-1$
        btnRecycle.setText(Translations.getString("JogControlsPanel.btnRecycle.text")); //$NON-NLS-1$
        panelSpecial.add(btnRecycle);

        panelActuators = new JPanel();
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Actuators"), null, panelActuators, null); //$NON-NLS-1$
        panelActuators.setLayout(new WrapLayout(WrapLayout.LEFT));

        JPanel panelSafety = new JPanel();
        tabbedPane_1.addTab(Translations.getString("JogControlsPanel.Tab.Safety"), null, panelSafety, null); //$NON-NLS-1$
        panelSafety.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));

        boardProtectionOverrideCheck = new JCheckBox(Translations.getString("JogControlsPanel.Label.OverrideBoardProtection")); //$NON-NLS-1$
        boardProtectionOverrideCheck.setToolTipText(Translations.getString("JogControlsPanel.Label.OverrideBoardProtection.Description")); //$NON-NLS-1$
        panelSafety.add(boardProtectionOverrideCheck, "1, 1"); //$NON-NLS-1$
    }

    private FocusTraversalPolicy focusPolicy = new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
            return sliderIncrements;
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
            return sliderIncrements;
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
            return sliderIncrements;
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
            return sliderIncrements;
        }

        @Override
        public Component getInitialComponent(Window window) {
            return sliderIncrements;
        }

        @Override
        public Component getLastComponent(Container aContainer) {
            return sliderIncrements;
        }
    };

    public double getSpeed() {
        return speedSlider.getValue() * 0.01D;
    }

    @SuppressWarnings("serial")
    public Action yPlusAction = new AbstractAction("Y+", Icons.arrowUp) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 1, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action yMinusAction = new AbstractAction("Y-", Icons.arrowDown) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, -1, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action xPlusAction = new AbstractAction("X+", Icons.arrowRight) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(1, 0, 0, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action xMinusAction = new AbstractAction("X-", Icons.arrowLeft) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(-1, 0, 0, 0);
        }
    };

    // Actions for added buttons
    @SuppressWarnings("serial")
    public Action startWidthScript = new AbstractAction("Ramp Width") { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            threadforConveyorWidthScript = new MultiThreadProcessforConveyorWidthScript();
            threadforConveyorWidthScript.start();
        }
    };
    @SuppressWarnings("serial")
    public Action actuateClamping = new AbstractAction("(Un)Actuate Clamping") { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            Machine machine = Configuration.get().getMachine();
            Actuator actuator = machine.getActuatorByName("Clamping");
            // Actuator actuator = actuators.get(0);
            UiUtils.submitUiMachineTask(() -> {
                AbstractActuator.assertOnOffDefined(actuator);
                actuator.actuate(!actuator.isActuated());
            });
            
        }
    };
    @SuppressWarnings("serial")
    public static Action jobScriptAction = new AbstractAction("n - Jobs Script") { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            if(threadforConveyorScript==null){
                isJobComplete=false;
                ishomingNow = false;
                setPcbAtFirstIrSensor = false;
                setPcbAtSecondIrSensor = false;
                threadforConveyorScript = new MultiThreadProcessforConveyorScript();
                threadforConveyorScript.start();
            }else{
                UI.showMessage("Already Running");
            }
        }
    };
    //End Actions Code


    @SuppressWarnings("serial")
    public Action zPlusAction = new AbstractAction("Z+", Icons.arrowUp) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, 1, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action zMinusAction = new AbstractAction("Z-", Icons.arrowDown) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, -1, 0);
        }
    };

    @SuppressWarnings("serial")
    public Action cPlusAction = new AbstractAction("C+", Icons.rotateCounterclockwise) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, 0, 1);
        }
    };

    @SuppressWarnings("serial")
    public Action cMinusAction = new AbstractAction("C-", Icons.rotateClockwise) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            jog(0, 0, 0, -1);
        }
    };

    @SuppressWarnings("serial")
    public Action xyParkAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.ParkXY"), Icons.park) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Head head = machineControlsPanel.getSelectedTool().getHead();
                if (head == null) {
                    head = Configuration.get()
                            .getMachine()
                            .getDefaultHead(); 
                }
                MovableUtils.park(head);
                MovableUtils.fireTargetedUserAction(head.getDefaultHeadMountable());
            });
        }
    };

    @SuppressWarnings("serial")
    public Action zParkAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.ParkZ"), Icons.park) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable hm = machineControlsPanel.getSelectedTool();
                // Note, we don't just moveToSafeZ(), because this will just sit still, if we're already in the Safe Z Zone.
                // instead we explicitly move to the Safe Z coordinate i.e. the lower bound of the Safe Z Zone, applicable
                // for this hm.
                Location location = hm.getLocation();
                Length safeZLength = hm.getSafeZ();
                double safeZ = (safeZLength != null ? safeZLength.convertToUnits(location.getUnits()).getValue() : Double.NaN);
                location = location.derive(null, null, safeZ, null);
                if (Configuration.get().getMachine().isSafeZPark()) {
                    // All other head-mountables must also be moved to safe Z.
                    hm.getHead().moveToSafeZ();
                }
                hm.moveTo(location);
                MovableUtils.fireTargetedUserAction(hm);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action cParkAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.ParkC"), Icons.park) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable hm = machineControlsPanel.getSelectedTool();
                Location location = hm.getLocation();
                double parkAngle = 0;
                if (hm instanceof AbstractNozzle) {
                    AbstractNozzle nozzle = (AbstractNozzle) hm;
                    if (nozzle.getRotationMode() == RotationMode.LimitedArticulation) {
                        if (nozzle.getPart() == null) {
                            // Make sure any lingering rotation offset is reset.
                            nozzle.setRotationModeOffset(null);
                        }
                        // Limited axis, select a 90° step position within the limits.
                        double [] limits = nozzle.getRotationModeLimits();
                        parkAngle = Math.round((limits[0]+limits[1])/2/90)*90;
                        if (parkAngle < limits[0] || parkAngle > limits[1]) {
                            // Rounded mid-point outside limits? Can this ever happen? If yes, fall back to exact mid-point.
                            parkAngle = (limits[1] + limits[0])/2;
                        }
                    }
                }
                location = location.derive(null, null, null, parkAngle);
                hm.moveTo(location);
                MovableUtils.fireTargetedUserAction(hm, true);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action safezAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.HeadSafeZ")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                HeadMountable hm = machineControlsPanel.getSelectedTool();
                Head head = hm.getHead();
                head.moveToSafeZ();
                MovableUtils.fireTargetedUserAction(hm);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action discardAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.Discard")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Nozzle nozzle = machineControlsPanel.getSelectedNozzle();
                Cycles.discard(nozzle);
            });
        }
    };

    @SuppressWarnings("serial")
    public Action recycleAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.Recycle")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Nozzle nozzle = machineControlsPanel.getSelectedNozzle();
                Part part = nozzle.getPart();

                // just make sure a part is there
                if (part == null) {
                    throw new Exception("No Part on the current nozzle!");
                }

                // go through the feeders
                for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
                    if (part.equals(feeder.getPart()) && feeder.isEnabled() && feeder.canTakeBackPart()) {
                        feeder.takeBackPart(nozzle);
                        break;
                    }
                }
            });
        }
    };


    @SuppressWarnings("serial")
    public Action raiseIncrementAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.RaiseJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(
                    Math.min(sliderIncrements.getMaximum(), sliderIncrements.getValue() + 1));
        }
    };

    @SuppressWarnings("serial")
    public Action lowerIncrementAction = new AbstractAction(Translations.getString("JogControlsPanel.Action.LowerJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(
                    Math.max(sliderIncrements.getMinimum(), sliderIncrements.getValue() - 1));
        }
    };

    @SuppressWarnings("serial")
    public Action setIncrement1Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.FirstJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(1);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement2Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.SecondJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(2);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement3Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.ThirdJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(3);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement4Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.FourthJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(4);
        }
    };
    @SuppressWarnings("serial")
    public Action setIncrement5Action = new AbstractAction(Translations.getString("JogControlsPanel.Action.FifthJogIncrement")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            sliderIncrements.setValue(5);
        }
    };


    private void addActuator(Actuator actuator) {
        String name = actuator.getHead() == null ? actuator.getName() : actuator.getHead()
                .getName()
                + ":" + actuator.getName(); //$NON-NLS-1$
                JButton actuatorButton = new JButton(name);
                actuatorButton.addActionListener((e) -> {
                    ActuatorControlDialog dlg = new ActuatorControlDialog(actuator);
                    dlg.pack();
                    dlg.revalidate();
                    dlg.setLocationRelativeTo(JogControlsPanel.this);
                    dlg.setVisible(true);
                });
                BeanUtils.addPropertyChangeListener(actuator, "name", e -> { //$NON-NLS-1$
                    actuatorButton.setText(
                            actuator.getHead() == null ? actuator.getName() : actuator.getHead()
                                    .getName()
                                    + ":" + actuator.getName()); //$NON-NLS-1$
                });
                panelActuators.add(actuatorButton);
                actuatorButtons.put(actuator, actuatorButton);
    }

    private void removeActuator(Actuator actuator) {
        panelActuators.remove(actuatorButtons.remove(actuator));
    }

    private ConfigurationListener configurationListener = new ConfigurationListener.Adapter() {
        @Override
        public void configurationComplete(Configuration configuration) throws Exception {
            setUnits(configuration.getSystemUnits());
            speedSlider.setValue((int) (configuration.getMachine()
                    .getSpeed()
                    * 100));

            panelActuators.removeAll();

            Machine machine = Configuration.get()
                    .getMachine();

            for (Actuator actuator : machine.getActuators()) {
                addActuator(actuator);
            }
            for (final Head head : machine.getHeads()) {
                for (Actuator actuator : head.getActuators()) {
                    addActuator(actuator);
                }
            }


            PropertyChangeListener listener = (e) -> {
                if (e.getOldValue() == null && e.getNewValue() != null) {
                    Actuator actuator = (Actuator) e.getNewValue();
                    addActuator(actuator);
                }
                else if (e.getOldValue() != null && e.getNewValue() == null) {
                    removeActuator((Actuator) e.getOldValue());
                }
            };

            BeanUtils.addPropertyChangeListener(machine, "actuators", listener); //$NON-NLS-1$
            for (Head head : machine.getHeads()) {
                BeanUtils.addPropertyChangeListener(head, "actuators", listener); //$NON-NLS-1$
            }


            setEnabled(machineControlsPanel.isEnabled());
            
            // add property listener for recycle button
            // enable recycle only if part on current head
            PropertyChangeListener recyclePropertyListener = (e) -> {
                    Nozzle selectedNozzle = machineControlsPanel.getSelectedNozzle();
                    if (selectedNozzle != null) {
                        boolean canTakeBack = false;
                        Part part = selectedNozzle.getPart();
                        if (part != null) {
                            for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
                                if (feeder.isEnabled() 
                                        && feeder.getPart() == part
                                        && feeder.canTakeBackPart()) {
                                    canTakeBack = true;
                                }
                            }
                        }
                        recycleAction.setEnabled(canTakeBack);
                    }
                };
            // add to all nozzles
            for (Head head : Configuration.get().getMachine().getHeads()) {
                for (Nozzle nozzle : head.getNozzles()) {
                    if (nozzle instanceof AbstractNozzle) {
                        AbstractNozzle aNozzle = (AbstractNozzle) nozzle;
                        aNozzle.addPropertyChangeListener("part", recyclePropertyListener);
                    }
                }
            }
            // add to the currently selected tool, so we get a notification if that changed, maybe other part on the nozzle
            machineControlsPanel.addPropertyChangeListener("selectedTool", recyclePropertyListener);
        }
    };

    private Map<Actuator, JButton> actuatorButtons = new HashMap<>();
    private JSlider speedSlider;
}
