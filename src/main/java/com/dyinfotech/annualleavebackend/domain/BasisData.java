package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "basis_data")
@Getter
@IdClass(BasisData.BasisDataId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BasisData {

    @Id
    @Column(name = "year", length = 4)
    private String year;

    @Id
    @Column(name = "seq")
    private Long seq;

    @Column(name = "type", length = 2)
    private String type;

    @Column(name = "data", length = 50)
    private String data;

    @Column(name = "remark", length = 200)
    private String remark;

    @Builder
    public BasisData(String year, Long seq, String type, String data, String remark) {
        this.year = year;
        this.seq = seq;
        this.type = type;
        this.data = data;
        this.remark = remark;
    }

    // Composite id defined as a static inner class so BasisData is self-contained.
    @Getter
    public static class BasisDataId implements java.io.Serializable {
        /**
		 * default serialVersionUID for Serializable
		 */
        private static final long serialVersionUID = -4265061405661642440L;
        
		private String year;
        private Long seq;

        public BasisDataId() {
        }

        public BasisDataId(String year, Long seq) {
            this.year = year;
            this.seq = seq;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BasisDataId that = (BasisDataId) o;
            return java.util.Objects.equals(year, that.year) && java.util.Objects.equals(seq, that.seq);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(year, seq);
        }
    }
}
