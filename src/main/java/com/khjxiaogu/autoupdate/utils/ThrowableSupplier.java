package com.khjxiaogu.autoupdate.utils;

@FunctionalInterface
interface ThrowableSupplier<T>{
	T get() throws Throwable;
}