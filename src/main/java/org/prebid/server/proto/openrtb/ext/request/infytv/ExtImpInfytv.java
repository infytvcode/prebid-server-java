package org.prebid.server.proto.openrtb.ext.request.infytv;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.{bidder}
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpInfytv {
    @JsonProperty("dsp_id")
    Integer dspId;

    @JsonProperty("customer_id")
    Integer customerId;

    @JsonProperty("tag_id")
    Integer tagId;

    @JsonProperty("base")
    String base;

    @JsonProperty("path")
    String path;

    @JsonProperty("dsp_type")
    String dspType;

    @JsonProperty("min_cpm")
    Double minCpm;

    @JsonProperty("max_cpm")
    Double maxCpm;
}
