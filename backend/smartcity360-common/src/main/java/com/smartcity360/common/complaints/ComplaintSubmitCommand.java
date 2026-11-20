package com.smartcity360.common.complaints; import java.util.UUID; public record ComplaintSubmitCommand(String title,String description,String severity,UUID zoneId){}
