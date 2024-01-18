package com.khjxiaogu.autoupdate.utils;
@FunctionalInterface
public interface ThrowableFunction<T, R> {
	R apply(T t) throws Throwable;
}
