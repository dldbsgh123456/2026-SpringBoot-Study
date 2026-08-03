package com.sist.web.vo;
// => public record BoardDTO => Spring AI
public interface BoardDTO {
    public int getNo();
    public String getName();
    public String getSubject();
    public String getDbday();
    public int getHit();
}
