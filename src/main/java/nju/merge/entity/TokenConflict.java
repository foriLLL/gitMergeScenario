package nju.merge.entity;

import com.github.javaparser.Token;

import java.util.ArrayList;
import java.util.List;

public class TokenConflict {
    public List<String> prefix;
    public List<String> suffix;
    public List<String> aRegion;
    public List<String> oRegion;
    public List<String> bRegion;


    public List<String> resolution;

    public List<String> a;
    public List<String> b;
    public List<String> o;


    public TokenConflict() {
    }

    public TokenConflict(List<String> prefix, List<String> suffix, List<String> aRegion, List<String> oRegion, List<String> bRegion, List<String> resolution) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.aRegion = aRegion;
        this.oRegion = oRegion;
        this.bRegion = bRegion;
        this.resolution = resolution;

        this.a = this.concat(prefix, aRegion, suffix);
        this.b = this.concat(prefix, bRegion, suffix);
        this.o = this.concat(prefix, oRegion, suffix);
    }

    private List<String> concat(List<String> prefix,  List<String> region, List<String> suffix){
        List<String> ret = new ArrayList<>(prefix);
        ret.addAll(region);
        ret.addAll(suffix);
        return ret;
    }

    @Override
    public String toString(){
        String ret = a.toString() + "\n" + o.toString() + "\n" + b.toString() + "\n";
        return ret;
    }
}