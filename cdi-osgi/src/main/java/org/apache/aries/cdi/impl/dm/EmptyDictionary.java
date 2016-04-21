package org.apache.aries.cdi.impl.dm;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;

/**
 * Created by gnodet on 21/04/16.
 */
class EmptyDictionary<K, V> extends Dictionary<K, V> {
    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Enumeration<K> keys() {
        return Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<V> elements() {
        return Collections.emptyEnumeration();
    }

    @Override
    public V get(Object key) {
        return null;
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }
}
