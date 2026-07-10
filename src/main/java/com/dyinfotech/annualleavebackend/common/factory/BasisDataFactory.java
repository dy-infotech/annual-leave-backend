package com.dyinfotech.annualleavebackend.common.factory;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.type.BasisDataParseType;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.domain.BasisData;
import com.dyinfotech.annualleavebackend.repository.BasisDataRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BasisDataFactory {
	private final BasisDataRepository repository;

	private Map<BasisDataType, BasisData> dataMap = Collections.emptyMap();
	
	private Map<BasisDataType, BasisData> loadByYear(int year) {
	    Map<BasisDataType, BasisData> grouped = new HashMap<>();

	    List<BasisData> list = repository.findByYear(String.valueOf(year));

	    for (BasisData b : list) {
	        grouped.put(BasisDataType.fromCode(b.getSeq()), b);
	    }

	    return grouped;
	}
	
	@PostConstruct
	private void init() {
		reload();
	}
	
	public void reload() {
		int currentYear = LocalDate.now().getYear();
		Map<BasisDataType, BasisData> grouped = loadByYear(currentYear);

	    if (grouped.isEmpty()) {
//	        log.warn("BasisData for {} not found. Using previous year data.", currentYear);
	        grouped = loadByYear(currentYear - 1);
	    }

		this.dataMap = Collections.unmodifiableMap(grouped);
	}

	public Optional<BasisData> get(BasisDataType type) {
		return Optional.ofNullable(dataMap.get(type));
	}
	public Optional<BasisData> get(Long seq) {
		return get(BasisDataType.fromCode(seq));
	}

	// Typed accessors based on schema: 0: bool, 1: int, 2: long, 3: float, 4: double, 5: string
	private Optional<Boolean> getAsBoolean(Optional<BasisData> basisData) {
		return basisData.map(this::parseBoolean);
	}
	public Optional<Boolean> getAsBoolean(BasisDataType type) {
		return getAsBoolean(get(type));
	}
	public Optional<Boolean> getAsBoolean(Long seq) {
		return getAsBoolean(get(seq));
	}

	private Optional<Integer> getAsInteger(Optional<BasisData> basisData) {
		return basisData.map(this::parseInteger);
	}
	public Optional<Integer> getAsInteger(BasisDataType type) {
		return getAsInteger(get(type));
	}
	public Optional<Integer> getAsInteger(Long seq) {
		return getAsInteger(get(seq));
	}

	private Optional<Long> getAsLong(Optional<BasisData> basisData) {
		return basisData.map(this::parseLong);
	}
	public Optional<Long> getAsLong(BasisDataType type) {
		return getAsLong(get(type));
	}
	public Optional<Long> getAsLong(Long seq) {
		return getAsLong(get(seq));
	}

	private Optional<Float> getAsFloat(Optional<BasisData> basisData) {
		return basisData.map(this::parseFloat);
	}
	public Optional<Float> getAsFloat(BasisDataType type) {
		return getAsFloat(get(type));
	}
	public Optional<Float> getAsFloat(Long seq) {
		return getAsFloat(get(seq));
	}

	private Optional<Double> getAsDouble(Optional<BasisData> basisData) {
		return basisData.map(this::parseDouble);
	}
	public Optional<Double> getAsDouble(BasisDataType type) {
		return getAsDouble(get(type));
	}
	public Optional<Double> getAsDouble(Long seq) {
		return getAsDouble(get(seq));
	}

	private Optional<String> getAsString(Optional<BasisData> basisData) {
		return basisData.map(BasisData::getData);
	}
	public Optional<String> getAsString(BasisDataType type) {
		return getAsString(get(type));
	}
	public Optional<String> getAsString(Long seq) {
		return getAsString(get(seq));
	}

	// Parsing helpers
	private Boolean parseBoolean(BasisData b) throws IllegalArgumentException {
		BasisDataParseType type = BasisDataParseType.fromCode(Integer.parseInt(b.getType()));
		if (type == null || type != BasisDataParseType.BOOLEAN) {
			throw new IllegalArgumentException("BasisData type is not BOOLEAN: " + b.getType() + ", year: " + b.getYear() + ", seq: " + b.getSeq());
		}
		
		String v = b.getData();
		if (v == null) return null;
		v = v.trim().toLowerCase();
		if (v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y")) return true;
		if (v.equals("false") || v.equals("0") || v.equals("no") || v.equals("n")) return false;
		
		throw new IllegalArgumentException("Cannot parse boolean from basis data: " + v);
	}

	private Integer parseInteger(BasisData b) throws IllegalArgumentException, NumberFormatException {
		BasisDataParseType type = BasisDataParseType.fromCode(Integer.parseInt(b.getType()));
		if (type == null || type != BasisDataParseType.INTEGER) {
			throw new IllegalArgumentException("BasisData type is not INTEGER: " + b.getType() + ", year: " + b.getYear() + ", seq: " + b.getSeq());
		}
		
		String v = b.getData();
		if (v == null) return null;
		
		return Integer.valueOf(v.trim());
	}

	private Long parseLong(BasisData b) throws IllegalArgumentException, NumberFormatException {
		BasisDataParseType type = BasisDataParseType.fromCode(Integer.parseInt(b.getType()));
		if (type == null || type != BasisDataParseType.LONG) {
			throw new IllegalArgumentException("BasisData type is not LONG: " + b.getType() + ", year: " + b.getYear() + ", seq: " + b.getSeq());
		}
		
		String v = b.getData();
		if (v == null) return null;
		
		return Long.valueOf(v.trim());
	}

	private Float parseFloat(BasisData b) throws IllegalArgumentException, NumberFormatException {
		BasisDataParseType type = BasisDataParseType.fromCode(Integer.parseInt(b.getType()));
		if (type == null || type != BasisDataParseType.FLOAT) {
			throw new IllegalArgumentException("BasisData type is not FLOAT: " + b.getType() + ", year: " + b.getYear() + ", seq: " + b.getSeq());
		}
		
		String v = b.getData();
		if (v == null) return null;
		
		return Float.valueOf(v.trim());
	}

	private Double parseDouble(BasisData b) throws IllegalArgumentException, NumberFormatException {
		BasisDataParseType type = BasisDataParseType.fromCode(Integer.parseInt(b.getType()));
		if (type == null || type != BasisDataParseType.DOUBLE) {
			throw new IllegalArgumentException("BasisData type is not DOUBLE: " + b.getType() + ", year: " + b.getYear() + ", seq: " + b.getSeq());
		}
		
		String v = b.getData();
		if (v == null) return null;
		
		return Double.valueOf(v.trim());
	}
}
