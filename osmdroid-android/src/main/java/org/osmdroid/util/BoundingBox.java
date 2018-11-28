// Created by plusminus on 19:06:38 - 25.09.2008
package org.osmdroid.util;

import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.MapView;

import java.io.Serializable;
import java.util.List;

import static org.osmdroid.util.MyMath.gudermann;
import static org.osmdroid.util.MyMath.gudermannInverse;

/**
 * @author Nicolas Gramlich
 * @author Andreas Schildbach
 */
public class BoundingBox implements Parcelable, Serializable {

	// ===========================================================
	// Constants
	// ===========================================================

	static final long serialVersionUID = 2L;

	// ===========================================================
	// Fields
	// ===========================================================

	private double mLatNorth;
	private double mLatSouth;
	private double mLonEast;
	private double mLonWest;

	// ===========================================================
	// Constructors
	// ===========================================================

	public BoundingBox(final double north, final double east, final double south, final double west) {
		set(north, east, south, west);
	}

	/**
	 * @since 6.0.2
	 * In order to avoid longitude and latitude checks that will crash
	 * in TileSystem configurations with a bounding box that doesn't include [0,0]
	 */
	public BoundingBox() {}

	/**
	 * @since 6.0.0
	 */
	public void set(final double north, final double east, final double south, final double west) {
		mLatNorth = north;
		mLonEast = east;
		mLatSouth = south;
		mLonWest = west;
		//validate the values
		//  30 > 0 OK
		// 30 < 0 not ok

        final TileSystem tileSystem = org.osmdroid.views.MapView.getTileSystem();
		if (!tileSystem.isValidLatitude(north))
			throw new IllegalArgumentException("north must be in " + tileSystem.toStringLatitudeSpan());
		if (!tileSystem.isValidLatitude(south))
			throw new IllegalArgumentException("south must be in " + tileSystem.toStringLatitudeSpan());
		if (!tileSystem.isValidLongitude(west))
			throw new IllegalArgumentException("west must be in " + tileSystem.toStringLongitudeSpan());
		if (!tileSystem.isValidLongitude(east))
			throw new IllegalArgumentException("east must be in " + tileSystem.toStringLongitudeSpan());
	}

	public BoundingBox clone(){
		return new BoundingBox(this.mLatNorth, this.mLonEast, this.mLatSouth, this.mLonWest);
	}

	/** @return the BoundingBox enclosing this BoundingBox and bb2 BoundingBox */
	public BoundingBox concat(BoundingBox bb2){
		return new BoundingBox(
				Math.max(this.mLatNorth, bb2.getLatNorth()),
				Math.max(this.mLonEast, bb2.getLonEast()),
				Math.min(this.mLatSouth, bb2.getLatSouth()),
				Math.min(this.mLonWest, bb2.getLonWest()));
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	/**
	 * Use {@link #getCenterWithDateLine()} instead to take date line into consideration
	 * @return GeoPoint center of this BoundingBox
	 */
	@Deprecated
	public GeoPoint getCenter() {
		return new GeoPoint((this.mLatNorth + this.mLatSouth) / 2.0,
				(this.mLonEast + this.mLonWest) / 2.0);
	}

	/**
	 * This version takes into consideration the date line
	 * @since 6.0.0
	 */
	public GeoPoint getCenterWithDateLine() {
		return new GeoPoint(getCenterLatitude(), getCenterLongitude());
	}

	public double getDiagonalLengthInMeters() {
		return new GeoPoint(this.mLatNorth, this.mLonWest).distanceToAsDouble(new GeoPoint(
				this.mLatSouth, this.mLonEast));
	}

	public double getLatNorth() {
		return this.mLatNorth;
	}

	public double getLatSouth() {
		return this.mLatSouth;
	}

	/**
	 * @since 6.0.0
	 */
	public double getCenterLatitude() {
		return (mLatNorth + mLatSouth) / 2.0;
	}

	/**
	 * @since 6.0.0
	 */
	public double getCenterLongitude() {
		return getCenterLongitude(mLonWest, mLonEast);
	}

	/**
	 * Compute the center of two longitudes
	 * Taking into account the case when "west is on the right and east is on the left"
	 * @since 6.0.0
	 */
	public static double getCenterLongitude(final double pWest, final double pEast) {
		double longitude = (pEast + pWest) / 2.0;
		if (pEast < pWest) {
			// center is on the other side of earth
			longitude += 180;
		}
		return org.osmdroid.views.MapView.getTileSystem().cleanLongitude(longitude);
	}

	/**
	 * @since 6.0.0
	 */
	public double getActualNorth() {
		return Math.max(mLatNorth, mLatSouth);
	}

	/**
	 * @since 6.0.0
	 */
	public double getActualSouth() {
		return Math.min(mLatNorth, mLatSouth);
	}

	public double getLonEast() {
		return this.mLonEast;
	}

	public double getLonWest() {
		return this.mLonWest;
	}

	public double getLatitudeSpan() {
		return Math.abs(this.mLatNorth - this.mLatSouth);
	}

	public double getLongitudeSpan() {
		return Math.abs(this.mLonEast - this.mLonWest);
	}

	/**
	 *
	 * @param aLatitude
	 * @param aLongitude
	 * @param reuse
	 * @return relative position determined from the upper left corner.<br>
	 *         {0,0} would be the upper left corner. {1,1} would be the lower right corner. {1,0}
	 *         would be the lower left corner. {0,1} would be the upper right corner.
	 */
	public PointF getRelativePositionOfGeoPointInBoundingBoxWithLinearInterpolation(
			final double aLatitude, final double aLongitude, final PointF reuse) {
		final PointF out = (reuse != null) ? reuse : new PointF();
		final float y = (float)((this.mLatNorth - aLatitude) / getLatitudeSpan());
		final float x = 1 - (float) ((this.mLonEast - aLongitude) / getLongitudeSpan());
		out.set(x, y);
		return out;
	}

	public PointF getRelativePositionOfGeoPointInBoundingBoxWithExactGudermannInterpolation(
			final double aLatitude, final double aLongitude, final PointF reuse) {
		final PointF out = (reuse != null) ? reuse : new PointF();
		final float y = (float) ((gudermannInverse(this.mLatNorth) - gudermannInverse(aLatitude)) / (gudermannInverse(this.mLatNorth) - gudermannInverse(this.mLatSouth)));
		final float x = 1 - (float) ((this.mLonEast - aLongitude) / getLongitudeSpan());
		out.set(x, y);
		return out;
	}

	public GeoPoint getGeoPointOfRelativePositionWithLinearInterpolation(final float relX,
			final float relY) {
	        final TileSystem tileSystem = MapView.getTileSystem();
		final double lat = this.mLatNorth - (this.getLatitudeSpan() * relY);
		final double lon = this.mLonWest + (this.getLongitudeSpan() * relX);
		return new GeoPoint(tileSystem.cleanLatitude(lat), tileSystem.cleanLongitude(lon));
	}

	public GeoPoint getGeoPointOfRelativePositionWithExactGudermannInterpolation(final float relX,
			final float relY) {
	        final TileSystem tileSystem = MapView.getTileSystem();
		final double gudNorth = gudermannInverse(this.mLatNorth);
		final double gudSouth = gudermannInverse(this.mLatSouth);
		final double lat = gudermann((gudSouth + (1 - relY) * (gudNorth - gudSouth)));
		final double lon = this.mLonWest + (this.getLongitudeSpan() * relX);
		return new GeoPoint(tileSystem.cleanLatitude(lat), tileSystem.cleanLongitude(lon));
	}

	public BoundingBox increaseByScale(final float pBoundingboxPaddingRelativeScale) {
		final GeoPoint pCenter = this.getCenter();
		final double mLatSpanPadded_2 = (this.getLatitudeSpan() * pBoundingboxPaddingRelativeScale) / 2.0;
		final double mLonSpanPadded_2 = (this.getLongitudeSpan() * pBoundingboxPaddingRelativeScale) / 2.0;

		return new BoundingBox(pCenter.getLatitude() + mLatSpanPadded_2,
				pCenter.getLongitude() + mLonSpanPadded_2, pCenter.getLatitude()
						- mLatSpanPadded_2, pCenter.getLongitude() - mLonSpanPadded_2);
	}

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	public String toString() {
		return new StringBuffer().append("N:").append(this.mLatNorth).append("; E:")
				.append(this.mLonEast).append("; S:").append(this.mLatSouth).append("; W:")
				.append(this.mLonWest).toString();
	}

	// ===========================================================
	// Methods
	// ===========================================================

	public GeoPoint bringToBoundingBox(final double aLatitude, final double aLongitude) {
		return new GeoPoint(Math.max(this.mLatSouth, Math.min(this.mLatNorth, aLatitude)),
				Math.max(this.mLonWest, Math.min(this.mLonEast, aLongitude)));
	}

	public static BoundingBox fromGeoPoints(final List<? extends IGeoPoint> partialPolyLine) {
		double minLat = Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
		for (final IGeoPoint gp : partialPolyLine) {
			final double latitude = gp.getLatitude();
			final double longitude = gp.getLongitude();

			minLat = Math.min(minLat, latitude);
			minLon = Math.min(minLon, longitude);
			maxLat = Math.max(maxLat, latitude);
			maxLon = Math.max(maxLon, longitude);
		}

		return new BoundingBox(maxLat, maxLon, minLat, minLon);
	}

	public boolean contains(final IGeoPoint pGeoPoint) {
		return contains(pGeoPoint.getLatitude(), pGeoPoint.getLongitude());
	}

	public boolean contains(final double aLatitude, final double aLongitude) {
		return ((aLatitude < this.mLatNorth) && (aLatitude > this.mLatSouth))
				&& ((aLongitude < this.mLonEast) && (aLongitude > this.mLonWest));
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	// ===========================================================
	// Parcelable
	// ===========================================================

	public static final Parcelable.Creator<BoundingBox> CREATOR = new Parcelable.Creator<BoundingBox>() {
		@Override
		public BoundingBox createFromParcel(final Parcel in) {
			return readFromParcel(in);
		}

		@Override
		public BoundingBox[] newArray(final int size) {
			return new BoundingBox[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel out, final int arg1) {
		out.writeDouble(this.mLatNorth);
		out.writeDouble(this.mLonEast);
		out.writeDouble(this.mLatSouth);
		out.writeDouble(this.mLonWest);
	}

	private static BoundingBox readFromParcel(final Parcel in) {
		final double latNorth = in.readDouble();
		final double lonEast = in.readDouble();
		final double latSouth = in.readDouble();
		final double lonWest = in.readDouble();
		return new BoundingBox(latNorth, lonEast, latSouth, lonWest);
	}

     @Deprecated
     public int getLatitudeSpanE6() {
          return (int)(getLatitudeSpan() * 1E6);
     }

     @Deprecated
     public int getLongitudeSpanE6() {
          return (int)(getLongitudeSpan() * 1E6);
     }
}
