//  Copyright 2020 VMware, Inc.
//  SPDX-License-Identifier: Apache-2.0
//

package com.idcta.proj.sensor.datatype;

import java.util.Date;

/// Raw location data for estimating indirect exposure
public class Location {
    /// Measurement values, e.g. GPS coordinates in comma separated string format for latitude and longitude
    public final LocationReference value;
    /// Time spent at location.
    public final Date start, end;

    public Location(LocationReference value, Date start, Date end) {
        this.value = value;
        this.start = start;
        this.end = end;
    }

    /// Get plain text description of proximity data
    public String description() {
        return (value == null ? "null" : value.description()) + ":[from=" + start + ",to=" + end + "]";
    }
}
