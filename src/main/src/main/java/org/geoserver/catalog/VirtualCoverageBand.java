package org.geoserver.catalog;

import java.io.Serializable;
import java.util.List;

public class VirtualCoverageBand implements Serializable{

//    @Override
//    public int hashCode() {
//        final int prime = 31;
//        int result = 1;
//        result = prime * result + ((coverageBands == null) ? 0 : coverageBands.hashCode());
//        return result;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj)
//            return true;
//        if (obj == null)
//            return false;
//        if (getClass() != obj.getClass())
//            return false;
//        VirtualCoverageBands other = (VirtualCoverageBands) obj;
//        if (coverageBands == null) {
//            if (other.coverageBands != null)
//                return false;
//        } else if (!coverageBands.equals(other.coverageBands))
//            return false;
//        return true;
//    }
//
//    private List<VirtualCoverageBand> coverageBands;
//    
//    public VirtualCoverageBands(List<VirtualCoverageBand> coverageBands) {
//        this.coverageBands = coverageBands;
//    }
    public static enum CompositionType {
        BAND_SELECT, MATH
    }
//    
//    public static class VirtualCoverageBand implements Serializable {
//    
        public VirtualCoverageBand(String coverageName, String definition,
                int index, CompositionType compositionType) {
            super();
            this.coverageName = coverageName;
            this.definition = definition;
            this.index = index;
            this.compositionType = compositionType;
        }
    
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((compositionType == null) ? 0 : compositionType.hashCode());
            result = prime * result + ((coverageName == null) ? 0 : coverageName.hashCode());
            result = prime * result + ((definition == null) ? 0 : definition.hashCode());
            return result;
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            VirtualCoverageBand other = (VirtualCoverageBand) obj;
            if (compositionType != other.compositionType)
                return false;
            if (coverageName == null) {
                if (other.coverageName != null)
                    return false;
            } else if (!coverageName.equals(other.coverageName))
                return false;
            if (definition == null) {
                if (other.definition != null)
                    return false;
            } else if (!definition.equals(other.definition))
                return false;
            return true;
        }
    
        String coverageName;
    
        String definition;
        
        int index;
    
        CompositionType compositionType;
    
        public String getCoverageName() {
            return coverageName;
        }
    
        public void setCoverageName(String coverageName) {
            this.coverageName = coverageName;
        }
    
        public String getDefinition() {
            return definition;
        }
    
        public void setDefinition(String definition) {
            this.definition = definition;
        }
    
        public int getIndex() {
            return index;
        }
    
        public void setIndex(int index) {
            this.index = index;
        }
    
        public CompositionType getCompositionType() {
            return compositionType;
        }
    
        public void setCompositionType(CompositionType compositionType) {
            this.compositionType = compositionType;
        }
    }

//    public VirtualCoverageBand getBand(int i) {
//        return coverageBands.get(i);
//    }
//    
//    public VirtualCoverageBand getBand(String coverageName) {
//        for (VirtualCoverageBand coverageBand : coverageBands) {
//            if (coverageBand.getCoverageName().equalsIgnoreCase(coverageName)) {
//                return coverageBand;
//            }
//        }
//        return null;
//    }
//    
//    public int getSize() {
//        return coverageBands != null ? coverageBands.size() : 0;
//    }
//}
