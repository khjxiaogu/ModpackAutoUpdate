package com.khjxiaogu.autoupdate.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

public class SupplierList<T> extends ArrayList<ThrowableSupplier<T>> implements Supplier<T> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public SupplierList() {
		super();
	}
	@SafeVarargs
	public SupplierList(ThrowableSupplier<T>...suppliers) {
		this(Arrays.asList(suppliers));
	}
	@SafeVarargs
	public <U> SupplierList(ThrowableFunction<U,T> fun,U...us) {
		this(us.length);
		for(U u:us)
			this.add(()->fun.apply(u));
	}
	public SupplierList(Collection<? extends ThrowableSupplier<T>> c) {
		super(c);
	}

	public SupplierList(int initialCapacity) {
		super(initialCapacity);
	}

	@Override
	public T get() {
		for(ThrowableSupplier<T> t:this) {
			try {
				return t.get();
			}catch(Throwable ex) {
			}
		}
		return null;
	}

}
