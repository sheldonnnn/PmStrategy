package com.cmbc.mds.forex.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MergeBaseInfo {
    private List<String> sources;
    private List<String> providers;
    private String symbol;
}
