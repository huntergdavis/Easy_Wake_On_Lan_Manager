package com.hunterdavis.easywakeonlanmanager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class EasyWakeOnLanManager extends Activity {
	
	String macAddress = null;
	 // setup our hidden sql text
    InventorySQLHelper macData = new InventorySQLHelper(this);
    ArrayAdapter<String> m_adapterForSpinner = null;
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
		OnClickListener SendButtonListner = new OnClickListener() {
			public void onClick(View v) {
				sendPacket(v.getContext());
			}
		};

		Button sendButton = (Button) findViewById(R.id.sendPacket);
		sendButton.setOnClickListener(SendButtonListner);
		

		
		// set an adapter for our spinner
		m_adapterForSpinner = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item);
		m_adapterForSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		Spinner spinner = (Spinner) findViewById(R.id.oldmacs);
		spinner.setAdapter(m_adapterForSpinner);
		
		
		spinner.setOnItemSelectedListener(new MyUnitsOnItemSelectedListener());
		
		// fill up our spinner item
		Cursor cursor = getMacsCursor();
		if(cursor.getCount()>0)
		{
			while (cursor.moveToNext()) {
				String mac = cursor.getString(1);
				m_adapterForSpinner.add(mac);
			}
		}
		else
		{
			spinner.setEnabled(false);
		}
    }
    
 // set up the listener class for spinner
	class MyUnitsOnItemSelectedListener implements OnItemSelectedListener {
		public void onItemSelected(AdapterView<?> parent, View view, int pos,
				long id) {
			//Resources res = getResources();
			//updateSqlValues(rowId, "units", unitsarray[pos]);
			EditText macText = (EditText) findViewById(R.id.MacAddress);
			Spinner spinner = (Spinner) findViewById(R.id.oldmacs);
			
			String spinnerText = spinner.getSelectedItem().toString();
			spinner.setEnabled(true);
			macText.setText(spinnerText);
		}

		public void onNothingSelected(AdapterView<?> parent) {
			// Do nothing.
		}
	}
    
    
    public void sendPacket(Context context)
    {
    	EditText macText = (EditText) findViewById(R.id.MacAddress);
    	String macString = macText.getText().toString().trim();
    	if(macString.length() != 17)
    	{
    		Toast.makeText(context, "Mac Addresses Are Of The Sort XX:XX:XX:XX:XX:XX",
					Toast.LENGTH_LONG).show();
    		return;
    	}
    	
    	EditText ipText = (EditText) findViewById(R.id.IpAddress);
    	String ipString = ipText.getText().toString().trim();
    	Pattern IP_ADDRESS
        = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
            + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
            + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
            + "|[1-9][0-9]|[0-9]))");
    	Matcher matcher = IP_ADDRESS.matcher(ipString);
    if (matcher.matches() == false) {
        // ip is not correct
    	Toast.makeText(context, "IP Addresses Are Numeric Octets ala 192.168.0.101",
				Toast.LENGTH_LONG).show();
		return;
    }
    	
		Cursor cursor = getMacsCursor();
		Boolean cursorFound = false;
		if(cursor.getCount()>0)
		{
			while (cursor.moveToNext()) {
				String mac = cursor.getString(1);
				if(mac.equalsIgnoreCase(macString))
				{
					cursorFound = true;
					break;
				}
			}
		}
    	if(cursorFound == false)
    	{
			SQLiteDatabase db = macData.getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(InventorySQLHelper.MACS, macString);
			long latestRowId = db.insert(InventorySQLHelper.TABLE, null,
					values);
			db.close();
			m_adapterForSpinner.add(macString);
    	}
    	
    	
    	// at this point we have a mac address, so send magic packet out!
    	 try {
             byte[] macBytes;
			try {
				macBytes = getMacBytes(macString);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				Toast.makeText(context, "Other Problem With Mac Address",
						Toast.LENGTH_LONG).show();
				return;
			}
             byte[] bytes = new byte[6 + 16 * macBytes.length];
             for (int i = 0; i < 6; i++) {
                 bytes[i] = (byte) 0xff;
             }
             for (int i = 6; i < bytes.length; i += macBytes.length) {
                 System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
             }
             
             InetAddress address = InetAddress.getByName(ipString);
             // why is port 7???
             DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, 7);
             DatagramSocket socket = new DatagramSocket();
             socket.send(packet);
             socket.close();
             
             Toast.makeText(context, "Wake On Lan Packet Sent",
     				Toast.LENGTH_LONG).show();
         }
         catch (Exception e) {
             System.out.println("Failed to send Wake-on-LAN packet: + e");
             System.exit(1);
         }

    	
    	
    }
    
    private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");
        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address.");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address.");
        }
        return bytes;
    }

    
	private Cursor getMacsCursor() {
		SQLiteDatabase db = macData.getReadableDatabase();
		Cursor cursor = db.query(InventorySQLHelper.TABLE, null, null, null,
				null, null, null);
		startManagingCursor(cursor);
		return cursor;
	}
    
}