package com.macaroon.piztor;

import android.os.Bundle;
import android.view.Menu;

public class InitAct extends PiztorAct {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		id = "initAct";
		super.onCreate(savedInstanceState);
		AppMgr.init();
		setContentView(R.layout.activity_init);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if (Infomation.token == -1)
			AppMgr.trigger(AppMgr.noToken);
		else {
			//TODO jump to main
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.init, menu);
		return true;
	}

}
