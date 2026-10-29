package com.steph.simeme.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record AddTokenRequest(
        @JsonAlias({"ca", "address", "tokenAddress", "mint"})
        String contractAddress,
        String symbol,
        String name,
        Integer decimals,
        String logoUri
) {}
