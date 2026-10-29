package com.steph.simeme.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BirdeyeTokenMetadataResponse(
        boolean success,
        MetadataData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetadataData(
            String address,
            String symbol,
            String name,
            Integer decimals,
            @JsonProperty("logo_uri") String logoUriSnake,
            String logoURI,
            String icon,
            BigDecimal price,
            BigDecimal liquidity
    ) {
        public String resolvedLogoUri() {
            if (logoURI != null && !logoURI.isBlank()) return logoURI;
            if (logoUriSnake != null && !logoUriSnake.isBlank()) return logoUriSnake;
            return icon;
        }
    }
}
