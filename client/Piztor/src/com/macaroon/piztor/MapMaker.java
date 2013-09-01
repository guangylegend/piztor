package com.macaroon.piztor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.R.drawable;
import android.util.Log;
import android.util.AttributeSet;
import android.annotation.SuppressLint;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.Activity;  
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;  
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;  
import android.widget.Toast;  
import android.view.View.OnClickListener;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.provider.Settings;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import com.baidu.mapapi.map.LocationData;
import com.baidu.mapapi.BMapManager;  
import com.baidu.mapapi.MKGeneralListener;
import com.baidu.mapapi.map.ItemizedOverlay;
import com.baidu.mapapi.map.MKMapTouchListener;
import com.baidu.mapapi.map.MKMapViewListener;  
import com.baidu.mapapi.map.MapController;  
import com.baidu.mapapi.map.MapPoi;  
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationOverlay;
import com.baidu.mapapi.map.MKOLSearchRecord;
import com.baidu.mapapi.map.MKOLUpdateElement;
import com.baidu.mapapi.map.OverlayItem;
import com.baidu.mapapi.map.PopupClickListener;
import com.baidu.mapapi.map.PopupOverlay;
import com.baidu.mapapi.map.MKOfflineMap;
import com.baidu.mapapi.map.MKOfflineMapListener;
import com.baidu.mapapi.search.MKPoiInfo;
import com.baidu.mapapi.search.MKPoiResult;
import com.baidu.platform.comapi.basestruct.GeoPoint; 

public class MapMaker extends Activity {
	
	// MapView controlling component
	private MapView mMapView = null;
	MapController mMapController = null;
	MKOfflineMap mOffline = null;

	// Default center
	private final static GeoPoint sjtuCenter = new GeoPoint((int)(31.032247 * 1E6), (int)(121.445937 * 1E6));

	// Map layers and items
	private MyOverlay mOverlay = null;
	private OverlayItem curItem = null;
	private LocationOverlay mLocationOverlay;
	private ArrayList<OverlayItem> mItems = null;
	private MapInfo preMapInfo = null;
	private MapInfo nowMapInfo = null;
	private Vector<UserInfo> freshManInfo = null;
	// hash from uid to overlay item
	private HashMap<Integer, OverlayItem> hash = null;

	// marker layer
	private MyOverlay markerOverlay;
	private OverlayItem nowMarker = null;

	// Popup component
	private PopupOverlay popLay = null;
	private TextView popupText = null;
	private TextView leftText = null;
	private View viewCache = null;
	private View popupInfo = null;
	private View popupLeft = null;
	private View popupRight = null;

	//misc
	private Context context;
	private LocationManager locationManager = null;
	boolean isGPSEnabled;
	private int[] myIcons;
	private Drawable[] myBM;
	private final int iconNum = 4;

	/**
	 * Constructor
	 */
	public MapMaker(MapView mapView, Context cc) {
		
		mMapView = mapView;
		mMapController = mMapView.getController();
		
		mMapController.setCenter(sjtuCenter);
		mMapController.setZoom(16);
		mMapController.setRotation(-22);
		mMapController.enableClick(true);

		context = cc;
		mLocationOverlay = null;
		mOverlay = null;
	}

	/**
	 * Layer for my location
	 */
	public class LocationOverlay extends MyLocationOverlay {
		
		public LocationOverlay(MapView mapView) {
			super(mapView);
		}
	}

	/**
	 * Layer for items(other users)
	 */
	public class MyOverlay extends ItemizedOverlay {
	
		public MyOverlay(Drawable defaultMaker, MapView mapView) {
			super(defaultMaker, mapView);
		}
	
		@Override
		public boolean onTap(int index) {
		
			if (index == 0 && nowMarker != null) {
				OverlayItem item = getItem(index);
				//TODO
				/////////////////////////////////////////////////////////
				popupText.setText("hour");
				leftText.setText("minute");
				Bitmap bitmap [] = {
						BMapUtil.getBitmapFromView(popupLeft),
						BMapUtil.getBitmapFromView(popupInfo),
						BMapUtil.getBitmapFromView(popupRight),
				};
				popLay.showPopup(bitmap, item.getPoint(),32);
			} else {
				OverlayItem item = getItem(index);
				popupText.setText("UID");
				Bitmap bitmap = BMapUtil.getBitmapFromView(popupInfo);
				popLay.showPopup(bitmap, item.getPoint(), 32);
			}
			return true;
		}

		@Override
		public boolean onTap(GeoPoint pt, MapView mapView) {
		
			if(popLay != null) {
				popLay.hidePop();
			}
			return false;
		}
	}
	
	/**
	 * Initialize offline map
	 */
	public void InitOfflineMap() {
	
		mOffline = new MKOfflineMap();
		mOffline.init(mMapController, new MKOfflineMapListener() {
			
			@Override
			public void onGetOfflineMapState(int type, int state) {
				switch (type) {
				case MKOfflineMap.TYPE_DOWNLOAD_UPDATE:
					MKOLUpdateElement update = mOffline.getUpdateInfo(state);
					break;
				case MKOfflineMap.TYPE_NEW_OFFLINE:
					Log.d("offline", String.format("add offline map %d", state));
					break;
				case MKOfflineMap.TYPE_VER_UPDATE:
					Log.d("offline", String.format("new offline map version"));
					break;
				}
			}
		});
		int num = mOffline.scan();
		String msg = "";
		if (num == 0) {
			//msg = "No offline map found. It may have been loaded already or misplaced.";
		} else {
			msg = String.format("Loaded %d offline maps.", num);
		}
		Log.d("offline", "inited");
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Initialize location layer
	 */
	public void InitLocationOverlay() {

		mLocationOverlay = new LocationOverlay(mMapView);
		LocationData locationData = new LocationData();
		mLocationOverlay.setData(locationData);
		mMapView.getOverlays().add(mLocationOverlay);
		mLocationOverlay.enableCompass();
		mMapView.refresh();
	}

	/**
	 * Initialize other users layer
	 */
	public void InitMyOverLay() {
		
		//TODO
		/////////////////////////////////////////////////////////////////
		hash = new HashMap<Integer, OverlayItem>();
		mOverlay = new MyOverlay(context.getResources().getDrawable(R.drawable.circle_red), mMapView);
		mMapView.getOverlays().add(mOverlay);
		markerOverlay = new MyOverlay(context.getResources().getDrawable(R.drawable.marker1), mMapView);
	}
	
	/**
	 * Initialize popup
	 */
	public void InitPopup() {
	
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		viewCache = inflater.inflate(R.layout.custom_text_view, null);
		popupInfo = (View)viewCache.findViewById(R.id.popinfo);
		popupLeft = (View)viewCache.findViewById(R.id.popleft);
		popupRight = (View)viewCache.findViewById(R.id.popright);
		popupText = (TextView)viewCache.findViewById(R.id.textcache);
		leftText = (TextView)viewCache.findViewById(R.id.popleft);

		PopupClickListener popListener = new PopupClickListener() {
		
			@Override
			public void onClickedPopup(int index) {
				// when the popup is clicked
				if (index == 0) {
					// do nothing
				}
				if (index == 2) {
					// remove current marker
					mOverlay.removeItem(nowMarker);
					nowMarker = null;
					mMapView.refresh();
					popLay.hidePop();
				}
			}
		};

		popLay = new PopupOverlay(mMapView, popListener);
	}

	/**
	 * Initialize touch listener
	 */
	// moved to main

	/**
	 * Initialize map
	 */
	public void InitMap() {
	
		InitLocationOverlay();
		InitMyOverLay();
		InitPopup();
		InitOfflineMap();
		myBM = new Drawable[20];
		initMyIcons();
		//InitTouchListenr();
	}

	public void initMyIcons() {
		myBM[0] = context.getResources().getDrawable(R.drawable.circle_red);
		myBM[1] = context.getResources().getDrawable(R.drawable.circle_green);
		myBM[2] = context.getResources().getDrawable(R.drawable.circle_glass);
		myBM[3] = context.getResources().getDrawable(R.drawable.circle_yellow);
		myBM[4] = context.getResources().getDrawable(R.drawable.circle_wood);
		}
	
	public Drawable getGroupIcon(int gid) {
		if (gid == Infomation.myInfo.section) return myBM[0];
		else return myBM[gid % iconNum];
	}
	
	/**
	 * Update location layer when new location is received
	 */
	public void UpdateLocationOverlay(LocationData locationData, boolean hasAnimation) {
	
		mLocationOverlay.setData(locationData);
		mMapView.refresh();
		if (hasAnimation) {
			mMapController.animateTo(new GeoPoint((int)(locationData.latitude * 1E6)
						, (int)(locationData.longitude * 1E6)));
		}
	}
	
	/**
	 * Update to draw other users
	 */
	public void UpdateMap(MapInfo mapInfo) {
	
		if (mapInfo == null) {
			if (mOverlay != null && mOverlay.getAllItem().size() != 0) {
				mOverlay.removeAll();
			}
			return;
		}
		freshManInfo = new Vector<UserInfo>();
		// first remove all old users that are not here now
		if (preMapInfo != null) {
			for (UserInfo i : preMapInfo.getVector()) {
				if (i.uid == Infomation.myInfo.uid) continue;
				if (mapInfo.getUserInfo(i.uid) == null) {
					mOverlay.removeItem(hash.get(i.uid));
					hash.remove(i.uid);
				}
			}
		}
		mMapView.refresh();

		// then update and add items
		for (UserInfo i : mapInfo.getVector()) {
			if (i.uid == Infomation.myInfo.uid) continue;
			if (hash.containsKey(i.uid) == false) {	
				GeoPoint p = new GeoPoint((int)(i.getLatitude() * 1E6), (int)(i.getLongitude() * 1E6));
				curItem = new OverlayItem(p, "USERNAME_HERE", "USER_SNIPPET_HERE");
				//TODO getDrawable
				///////////////////////////////
				curItem.setMarker(getGroupIcon(i.section));
				mOverlay.addItem(curItem);
				hash.put(i.uid, curItem);
				//if (mMapView != null)
				//	mMapView.refresh();
			} else {
				GeoPoint p = new GeoPoint((int)(i.getLatitude() * 1E6), (int)(i.getLongitude() * 1E6));
				curItem = hash.get(i.uid);
				curItem.setGeoPoint(p);
				mOverlay.updateItem(curItem);
				//if (mMapView != null)
				//	mMapView.refresh();
			}
		}
		if (mMapView != null) {
			mMapView.refresh();
		}
		preMapInfo = mapInfo;
	}
	
	/**
	 * Draw a marker
	 */
	public void DrawMarker(GeoPoint markerPoint) {
	
		if (nowMarker != null) {
			nowMarker.setGeoPoint(markerPoint);
			mOverlay.updateItem(nowMarker);
			mMapView.refresh();
			mMapController.animateTo(markerPoint);
			return;
		}
		nowMarker = new OverlayItem(markerPoint, "MARKER_NAME", "");
		nowMarker.setMarker(context.getResources().getDrawable(R.drawable.marker_red));
		Log.d("marker", "new marker created");
		mOverlay.addItem(nowMarker);
		mMapView.refresh();
		mMapController.animateTo(markerPoint);
	}

	public GeoPoint getMakerLocation() {
		if (nowMarker == null) return null;
		else return nowMarker.getPoint();
	}
	
	public void removeMarker() {
		if (nowMarker == null) return;
		mOverlay.removeItem(nowMarker);
		nowMarker = null;
		mMapView.refresh();
	}
	
	/**
	 * Remove all other users
	 */
	public void clearOverlay(View view) {
		
		if(mOverlay != null && mOverlay.getAllItem().size() != 0) {
			mOverlay.removeAll();
			mMapView.refresh();
		}	
	}

	/**
	 * Reset other users over lay
	 */
	public void resetOverlay(View view) {

		clearOverlay(null);
		mOverlay.addItem(mItems);
	}
	
	@Override
	protected void onPause() {
		mMapView.onPause();
	}

	@Override 
	protected void onResume() {
		mMapView.onResume();
		}

	@Override
	protected void onDestroy() {
		mMapView.destroy();
	}
	
}