(function () {
    'use strict';

    const outOfChina = function (longitude, latitude) {
        return longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271;
    };

    const transformLatitude = function (longitude, latitude) {
        let result = -100 + 2 * longitude + 3 * latitude + 0.2 * latitude * latitude
            + 0.1 * longitude * latitude + 0.2 * Math.sqrt(Math.abs(longitude));
        result += (20 * Math.sin(6 * longitude * Math.PI) + 20 * Math.sin(2 * longitude * Math.PI)) * 2 / 3;
        result += (20 * Math.sin(latitude * Math.PI) + 40 * Math.sin(latitude / 3 * Math.PI)) * 2 / 3;
        result += (160 * Math.sin(latitude / 12 * Math.PI) + 320 * Math.sin(latitude * Math.PI / 30)) * 2 / 3;
        return result;
    };

    const transformLongitude = function (longitude, latitude) {
        let result = 300 + longitude + 2 * latitude + 0.1 * longitude * longitude
            + 0.1 * longitude * latitude + 0.1 * Math.sqrt(Math.abs(longitude));
        result += (20 * Math.sin(6 * longitude * Math.PI) + 20 * Math.sin(2 * longitude * Math.PI)) * 2 / 3;
        result += (20 * Math.sin(longitude * Math.PI) + 40 * Math.sin(longitude / 3 * Math.PI)) * 2 / 3;
        result += (150 * Math.sin(longitude / 12 * Math.PI) + 300 * Math.sin(longitude / 30 * Math.PI)) * 2 / 3;
        return result;
    };

    const wgs84ToGcj02 = function (longitude, latitude) {
        if (outOfChina(longitude, latitude)) {
            return [longitude, latitude];
        }
        const earthRadius = 6378245;
        const eccentricity = 0.006693421622965943;
        let latitudeDelta = transformLatitude(longitude - 105, latitude - 35);
        let longitudeDelta = transformLongitude(longitude - 105, latitude - 35);
        const radianLatitude = latitude / 180 * Math.PI;
        let magic = Math.sin(radianLatitude);
        magic = 1 - eccentricity * magic * magic;
        const squareRootMagic = Math.sqrt(magic);
        latitudeDelta = latitudeDelta * 180
            / ((earthRadius * (1 - eccentricity)) / (magic * squareRootMagic) * Math.PI);
        longitudeDelta = longitudeDelta * 180
            / (earthRadius / squareRootMagic * Math.cos(radianLatitude) * Math.PI);
        return [longitude + longitudeDelta, latitude + latitudeDelta];
    };

    window.TravelCoordinates = Object.freeze({ wgs84ToGcj02: wgs84ToGcj02 });
}());
